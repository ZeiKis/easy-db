package utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import model.command.Command;

import java.io.*;
import java.util.HashMap;
import java.util.zip.GZIPOutputStream;

public class CompressorUtil {
    public static void compress(File source) {
        String sourcePath = source.getAbsolutePath();
        String newPath = sourcePath + ".new";

        // 存储最新命令，map去重
        HashMap<String, Command> latestCommands = new HashMap<>();

        // 1. 加载所有命令到内存，保留最新的
        try (RandomAccessFile reader = new RandomAccessFile(source, "r")) {
            long len = reader.length();
            long pos = 0;

            while (pos < len) {
                int cmdLen = reader.readInt();
                byte[] bytes = new byte[cmdLen];
                reader.read(bytes);
                pos += 4 + cmdLen;

                String jsonStr = new String(bytes);
                if (!isValidJson(jsonStr)) continue;

                JSONObject value = JSON.parseObject(jsonStr);
                Command command = CommandUtil.jsonToCommand(value);
                if (command == null) continue;

                latestCommands.put(command.getKey(), command);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 2. 写入新的文件，仅保留最终状态
        try (RandomAccessFile writer = new RandomAccessFile(newPath, "rw")) {
            for (Command cmd : latestCommands.values()) {
                byte[] bytes = JSON.toJSONBytes(cmd);
                writer.writeInt(bytes.length);
                writer.write(bytes);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 3. 替换旧文件
        if (!source.delete() || !new File(newPath).renameTo(source)) {
            System.err.println("Failed to replace file: " + sourcePath);
        }
    }

    // 辅助方法：判断是否是合法 JSON
    private static boolean isValidJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return false;
        try {
            JSON.parse(jsonStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}