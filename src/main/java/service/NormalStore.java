/*
 *@Type NormalStore.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 02:07
 * @version
 */
package service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import model.command.Command;
import model.command.CommandPos;
import model.command.RmCommand;
import model.command.SetCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.CommandUtil;
import utils.CompressorUtil;
import utils.LoggerUtil;
import utils.RandomAccessFileUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NormalStore implements Store {

    public static final String TABLE = ".table";
    public static final String RW_MODE = "rw";
    public static final String NAME = "data";
    private final Logger LOGGER = LoggerFactory.getLogger(NormalStore.class);
    private final String logFormat = "[NormalStore][{}]: {}";
    private static final int MEMTABLE_THRESHOLD = 1000; // 内存表最大条目数，默认1000
    private static final long MAX_FILE_SIZE = 10*1024*1024; // 数据文件最大内存，默认10M 10*1024*1024
    private static int fileIndex = 1; // 标记数据文件索引
    private static final ExecutorService COMPRESSOR_POOL = Executors.newFixedThreadPool(2);// 创建一个线程池，用于压缩数据文件

    /**
     * 内存表，类似缓存
     */
    private TreeMap<String, Command> memTable;

    /**
     * hash索引，存的是数据长度和偏移量
     * */
    private HashMap<String, CommandPos> index;

    /**
     * 数据目录
     */
    private final String dataDir;

    /**
     * 读写锁，支持多线程，并发安全写入
     */
    private final ReadWriteLock indexLock;

    /**
     * 暂存数据的日志句柄
     */
    private RandomAccessFile writerReader;

    /**
     * 持久化阈值
     */
//    private final int storeThreshold;

    /**
     * WAL 日志
     */
    private WalLog walLog;


    public NormalStore(String dataDir) throws IOException {
        this.dataDir = dataDir;
        this.indexLock = new ReentrantReadWriteLock();
        this.memTable = new TreeMap<String, Command>();//暂存命令的缓存
        this.index = new HashMap<>();
        this.walLog = new WalLog();

        // 检查并创建数据目录
        File file = new File(dataDir);
        if (!file.exists()) {
            LoggerUtil.info(LOGGER, logFormat, "NormalStore", "dataDir isn't exist, creating...");
            file.mkdirs();
        }

        this.reloadIndex();

        // 回放 WAL 日志
        for (Command command : walLog.replay()) {
            if (command instanceof SetCommand) {
                memTable.put(command.getKey(), command);
            } else if (command instanceof RmCommand) {
                memTable.put(command.getKey(), command);
            }
        }

        // 添加关闭钩子，在JVM关闭时将内存表中的值写回table
        Runtime.getRuntime().addShutdownHook(new Thread(this::flushMemTableToDisk));
    }

    // 动态生成 table 文件名
    public String getFilePath() {
        return this.dataDir + File.separator + NAME + "_" + fileIndex + TABLE;
    }

    // 动态生成旧的 table 文件名
    public String getCurrentFilePath() {
        return this.dataDir + File.separator + NAME + "_" + (fileIndex - 1) + TABLE;
    }

    /**
     * 获取文件数量
     * @return
     */
    public int getfileCount() {
        int index = 1;
        // 遍历所有数据文件，记录文件数量
        while (true) {
            String path = dataDir + File.separator + NAME + "_" + index + TABLE;
            File file = new File(path);
            if (!file.exists()) break;
            index++;
        }
        return index - 1;
    }

    /**
     * 从文件加载索引
     */
    public void reloadIndex() {
        int index = getfileCount();
        String path;
        for (int i = 1; i <= index; i++) {
            path = dataDir + File.separator + NAME + "_" + i + TABLE;
            File file = new File(path);
            try (RandomAccessFile raf = new RandomAccessFile(file, RW_MODE)) {
                long len = raf.length();
                long start = 0;
                raf.seek(start);

                while (start < len) {
                    int cmdLen = raf.readInt();
                    byte[] bytes = new byte[cmdLen];
                    raf.read(bytes);

                    JSONObject value = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
                    Command command = CommandUtil.jsonToCommand(value);

                    start += 4;
                    if (command != null) {
                        CommandPos cmdPos = new CommandPos((int) start, cmdLen);
                        this.index.put(command.getKey(), cmdPos);
                    }
                    start += cmdLen;
                }

                LoggerUtil.debug(LOGGER, logFormat, "reload index: " + i);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void set(String key, Object value) {
        try {
            // 记录 WAL 文件
            SetCommand setCommand = new SetCommand(key, value);
            walLog.write(setCommand); // 直接写入 SetCommand
            // 加锁
            indexLock.writeLock().lock();
            // 先写入内存表
            memTable.put(key, new SetCommand(key, value));
            // 检查内存表是否达到阈值
            if (memTable.size() >= MEMTABLE_THRESHOLD) {
                flushMemTableToDisk(); // 刷盘
            }
        } catch (Throwable t) {
            LoggerUtil.debug(LOGGER, logFormat, "set操作异常: 正在执行刷盘...");
            flushMemTableToDisk();
            throw new RuntimeException(t);
        } finally {
            indexLock.writeLock().unlock();//释放锁
        }
    }

    @Override
    public Object get(String key) {
        try {
            indexLock.readLock().lock();//读锁，允许多个线程同时读

            // 先查内存表，有则返回，没有再去查索引表和磁盘
            // 如果所查数据不在内存表中，说明要么数据在磁盘中，要么数据不存在（可能被删除）
            Command cmdInMem = memTable.get(key);
            if (cmdInMem != null) {
                if (cmdInMem instanceof SetCommand) {
                    return ((SetCommand) cmdInMem).getValue();
                } else if (cmdInMem instanceof RmCommand) {
                    return null;
                }
            }

            // 从索引中获取信息
            CommandPos cmdPos = index.get(key);
            if (cmdPos == null) {
                return null;
            }

            //通过索引获取命令 // TODO 接受到不是一个完整的二进制字符串，需要处理；
            byte[] commandBytes = RandomAccessFileUtil.readByIndex(this.getFilePath(), cmdPos.getPos(), cmdPos.getLen());
            String jsonStr = new String(commandBytes);
            if (!isValidJson(jsonStr)) {
                // 拿着commandBytes去每个文件里查找合法的json，直到找到一个合法json
                for (int i = 1; i <= fileIndex; i++) {
                    String path = dataDir + File.separator + NAME + "_" + i + TABLE;
                    commandBytes = RandomAccessFileUtil.readByIndex(path, cmdPos.getPos(), cmdPos.getLen());
                    if (isValidJson(new String(commandBytes))) {
                        jsonStr = new String(commandBytes);
                        JSONObject value = JSONObject.parseObject(jsonStr);
                        Command cmd = CommandUtil.jsonToCommand(value);
                        if (cmd.getKey() == key){
                            //如果是 SetCommand，表示该 key 有效，返回其值。
                            if (cmd instanceof SetCommand) {
                                return ((SetCommand) cmd).getValue();
                            }
                            //如果是 RmCommand，表示该 key 已被删除，返回 null。
                            if (cmd instanceof RmCommand) {
                                return null;
                            }
                        }
                    }
                }
                if (!isValidJson(jsonStr))
                    return null;
            }

            JSONObject value = JSONObject.parseObject(jsonStr);
            Command cmd = CommandUtil.jsonToCommand(value);

            //如果是 SetCommand，表示该 key 有效，返回其值。
            if (cmd instanceof SetCommand) {
                return ((SetCommand) cmd).getValue();
            }
            //如果是 RmCommand，表示该 key 已被删除，返回 null。
            if (cmd instanceof RmCommand) {
                return null;
            }

        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            indexLock.readLock().unlock();//释放锁
        }
        return null;
    }

    @Override
    public void rm(String key) {
        try {
            // 记录 WAL 文件
            RmCommand rmCommand = new RmCommand(key);
            walLog.write(rmCommand); // 直接写入 RmCommand
            // 加锁
            indexLock.writeLock().lock();
            // 先写入内存表
            memTable.put(key, new RmCommand(key));
            // 检查内存表是否达到阈值
            if (memTable.size() >= MEMTABLE_THRESHOLD) {
                flushMemTableToDisk(); // 刷盘
            }
        } catch (Throwable t) {
            LoggerUtil.debug(LOGGER, logFormat, "rm操作异常: 正在执行刷盘...");
            flushMemTableToDisk();
            throw new RuntimeException(t);
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {

    }

    /**
     * 将内存表刷盘到磁盘，将更新索引操作放在刷盘操作中
     * 这样设计的话，get操作就必须得先去查内存表
     */
    private void flushMemTableToDisk() {
        try {
//            throw new RuntimeException("异常测试");
            String currentFilePath = getFilePath();
            File currentFile = new File(currentFilePath);

            for (Command cmd : memTable.values()) {
                byte[] bytes = JSON.toJSONBytes(cmd);

                // 检查是否需要 Rotate
                if (currentFile.exists() && currentFile.length() + bytes.length > MAX_FILE_SIZE) {
                    rotateFile();
                    currentFilePath = getFilePath();
                    currentFile = new File(currentFilePath);
                }

                // 写入长度 + 数据
                RandomAccessFileUtil.writeInt(currentFilePath, bytes.length);
                int posInData = RandomAccessFileUtil.write(currentFilePath, bytes);

                CommandPos cmdPos = new CommandPos(posInData, bytes.length);
                index.put(cmd.getKey(), cmdPos);
            }

            memTable.clear();
            walLog.delete(); // 删除 WAL 文件
        } catch (Exception e) {
            LoggerUtil.error(LOGGER, e, "刷盘失败，WAL 文件保留");
            throw new RuntimeException("刷盘失败，保留 WAL 文件用于恢复", e);
        }
    }

    /**
     * 异步压缩文件
     */
    private void rotateFile() {
        String oldPath = getCurrentFilePath();
        fileIndex++;

        File oldFile = new File(oldPath);

        // 异步压缩该文件
        COMPRESSOR_POOL.submit(() -> {
            CompressorUtil.compress(oldFile);
        });

    }

    /**
     * 判断是否是合法的json字符串
     * @param jsonStr
     * @return
     */
    private boolean isValidJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return false;
        try {
            JSON.parse(jsonStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
