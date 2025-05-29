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
import java.util.Random;
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
    private static final int MEMTABLE_THRESHOLD = 1; // 内存表最大条目数，默认1000
    private static final long MAX_FILE_SIZE = 1024; // 数据文件最大内存，默认10M 10*1024*1024
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

    public NormalStore(String dataDir) {
        this.dataDir = dataDir;
        this.indexLock = new ReentrantReadWriteLock();
        this.memTable = new TreeMap<String, Command>();//暂存命令的缓存
        this.index = new HashMap<>();

        //检查并创建数据目录
        File file = new File(dataDir);
        if (!file.exists()) {
            LoggerUtil.info(LOGGER,logFormat, "NormalStore","dataDir isn't exist,creating...");
            file.mkdirs();
        }
        this.reloadIndex();//加载已有索引
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
     * 从文件加载索引
     */
    public void reloadIndex() {
        int index = 1;
        // 遍历所有数据文件，记录文件数量
        while (true) {
            String path = dataDir + File.separator + NAME + "_" + index + TABLE;
            File file = new File(path);
            if (!file.exists()) break;
            index++;
        }

        // 随机选取一半文件，加载索引，防止内存占用过多
        Random random = new Random();
        int[] randomIndex = new int[index / 2];
        for (int i = 0; i < randomIndex.length; i++) {
            randomIndex[i] = random.nextInt(index);
        }

        for (int i = 0; i < randomIndex.length; i++) {
            String path = dataDir + File.separator + NAME + "_" + randomIndex[i] + TABLE;
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

                LoggerUtil.debug(LOGGER, logFormat, "reload index: "+ randomIndex[i]);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    @Override
    public void set(String key, String value) {
        try {
            SetCommand command = new SetCommand(key, value);//set命令对象
            byte[] commandBytes = JSONObject.toJSONBytes(command);
            // 加锁
            indexLock.writeLock().lock();
            // 先写入内存表
            memTable.put(key, new SetCommand(key, value));
            // 检查内存表是否达到阈值
            if (memTable.size() >= MEMTABLE_THRESHOLD) {
                flushMemTableToDisk(); // 刷盘
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            indexLock.writeLock().unlock();//释放锁
        }
    }

    @Override
    public String get(String key) {
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
            //通过索引获取命令
            byte[] commandBytes = RandomAccessFileUtil.readByIndex(this.getFilePath(), cmdPos.getPos(), cmdPos.getLen());

            JSONObject value = JSONObject.parseObject(new String(commandBytes));
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
            RmCommand command = new RmCommand(key);//删除命令对象
            byte[] commandBytes = JSONObject.toJSONBytes(command);//转成二进制
            // 加锁
            indexLock.writeLock().lock();
            // 先写入内存表
            memTable.put(key, new RmCommand(key));
            // 检查内存表是否达到阈值
            if (memTable.size() >= MEMTABLE_THRESHOLD) {
                flushMemTableToDisk(); // 刷盘
            }
        } catch (Throwable t) {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 压缩文件逻辑
     */
    private void rotateFile() {
        String oldPath = getCurrentFilePath();
        fileIndex++;

        File oldFile = new File(oldPath);

        // 异步压缩该文件
        compressFileAsync(oldFile);
    }

    /**
     * 异步压缩文件执行
     */
    private void compressFileAsync(File file) {
        COMPRESSOR_POOL.submit(() -> {
            CompressorUtil.compress(file);
        });
    }

}
