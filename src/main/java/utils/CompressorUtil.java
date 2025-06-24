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

//    public static void compress(File source) {
//        // 创建备份文件：data/data_1.table -> data/data_1.table.bak
//        File backupFile = new File(source.getAbsolutePath() + ".bak");
//
//        // 创建压缩文件路径：data/data_1.table.bak.gz
//        String compressedFilePath = source.getAbsolutePath() + ".gz";
//
//        try {
//            // 先复制原始文件到 .bak 文件
//            copyFile(source, backupFile);
//
//            // 然后压缩 .bak 文件
//            try (
//                    FileInputStream fis = new FileInputStream(backupFile);
//                    FileOutputStream fos = new FileOutputStream(compressedFilePath);
//                    GZIPOutputStream gzipOS = new GZIPOutputStream(fos)
//            ) {
//                byte[] buffer = new byte[1024];
//                int len;
//                while ((len = fis.read(buffer)) != -1) {
//                    gzipOS.write(buffer, 0, len);
//                }
//                gzipOS.finish();
//            }
//
//            // 删除 .bak 文件
//            if (!backupFile.delete()) {
//                System.err.println("Failed to delete backup file: " + backupFile.getAbsolutePath());
//            }
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    // 实现文件复制功能
//    private static void copyFile(File source, File dest) throws IOException {
//        try (FileInputStream fis = new FileInputStream(source);
//             FileOutputStream fos = new FileOutputStream(dest)) {
//            byte[] buffer = new byte[1024];
//            int len;
//            while ((len = fis.read(buffer)) > 0) {
//                fos.write(buffer, 0, len);
//            }
//        }
//    }
}