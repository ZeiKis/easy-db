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
import controller.SocketServerHandler;
import model.command.Command;
import model.command.CommandPos;
import model.command.RmCommand;
import model.command.SetCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.CommandUtil;
import utils.LoggerUtil;
import utils.RandomAccessFileUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.jar.JarEntry;

public class NormalStore implements Store {

    public static final String TABLE = ".table";
    public static final String RW_MODE = "rw";
    public static final String NAME = "data";
    private final Logger LOGGER = LoggerFactory.getLogger(NormalStore.class);
    private final String logFormat = "[NormalStore][{}]: {}";
    private static final int MEMTABLE_THRESHOLD = 2; // 内存表最大条目数，默认1000


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

    public String genFilePath() {
        return this.dataDir + File.separator + NAME + TABLE;
    }

    /**
     * 从文件加载索引
     */
    public void reloadIndex() {
        try {
            RandomAccessFile file = new RandomAccessFile(this.genFilePath(), RW_MODE);//打开文件
            long len = file.length();
            long start = 0;
            file.seek(start);//设置文件指针到开头
            while (start < len) {
                int cmdLen = file.readInt();//先从文件中读取二进制命令长度
                byte[] bytes = new byte[cmdLen];//创建对应长度的byte数组，用来存储二进制原始命令数据
                file.read(bytes);//将二进制命令写入数组
                JSONObject value = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));//再转成json
                Command command = CommandUtil.jsonToCommand(value);//将json转具体的命令对象command
                start += 4;
                if (command != null) {
                    CommandPos cmdPos = new CommandPos((int) start, cmdLen);
                    index.put(command.getKey(), cmdPos);
                }
                start += cmdLen;
            }
            file.seek(file.length());
        } catch (Exception e) {
            e.printStackTrace();
        }
        LoggerUtil.debug(LOGGER, logFormat, "reload index: "+index.toString());
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
            byte[] commandBytes = RandomAccessFileUtil.readByIndex(this.genFilePath(), cmdPos.getPos(), cmdPos.getLen());

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
            for (Command cmd : memTable.values()) {
                byte[] bytes = JSON.toJSONBytes(cmd); // 拿到二进制命令数据
                RandomAccessFileUtil.writeInt(genFilePath(), bytes.length); // 写入命令长度
                int pos = RandomAccessFileUtil.write(genFilePath(), bytes); // 写入命令内容，并得到偏移量
                CommandPos cmdPos = new CommandPos(pos, bytes.length);
                index.put(cmd.getKey(), cmdPos); // 更新索引
            }
            memTable.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
