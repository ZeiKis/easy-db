package service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import model.command.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.CommandUtil;
import utils.LoggerUtil;
import utils.RandomAccessFileUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class WalLog {
    private static final String WAL_FILE = "data" + File.separator + "db.wal"; // 固定文件名
    private String walFile = WAL_FILE;
    private static final Logger LOGGER = LoggerFactory.getLogger(WalLog.class);

    public WalLog() {
    }

    /**
     *  写入 WAL 日志
     */
    public void write(Command command) {
        byte[] bytes = JSON.toJSONBytes(command);
        // 先写入长度
        RandomAccessFileUtil.writeInt(walFile, bytes.length);
        // 再写入数据
        RandomAccessFileUtil.write(walFile, bytes);
    }

     /**
     *  重放 WAL 日志
     * @return
     */
     public List<Command> replay() {
         List<Command> entries = new ArrayList<>();
         File file = new File(walFile);
         if (!file.exists()) return entries;

         try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
             long len = reader.length();
             long pos = 0;
             while (pos < len) {
                 int cmdLen = reader.readInt(); // 读取长度
                 byte[] bytes = new byte[cmdLen];
                 reader.read(bytes); // 读取数据
                 pos += 4 + cmdLen;

                 try {
                     String jsonStr = new String(bytes, StandardCharsets.UTF_8);
                     JSONObject value = JSON.parseObject(jsonStr);
                     Command command = CommandUtil.jsonToCommand(value);
                     if (command != null) {
                         entries.add(command);
                     }
                 } catch (Exception e) {
                     LoggerUtil.info(LOGGER, "WAL 条目损坏，跳过");
                 }
             }
         } catch (IOException e) {
             LoggerUtil.error(LOGGER, e, "WAL 文件严重损坏");
         }

         return entries;
     }

    /**
     * 删除 WAL 日志文件
     */
    public void delete() {
        File file = new File(walFile);
        if (file.exists()) {
            boolean success = file.delete();
            if (!success) {
                LoggerUtil.info(LOGGER, "Failed to delete WAL file: {}", walFile);
            }
        }
    }
}
