package utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class CompressorUtil {
    public static void compress(File source) {
        // 创建备份文件：data/data_1.table -> data/data_1.table.bak
        File backupFile = new File(source.getAbsolutePath() + ".bak");

        // 创建压缩文件路径：data/data_1.table.bak.gz
        String compressedFilePath = source.getAbsolutePath() + ".gz";

        try {
            // 先复制原始文件到 .bak 文件
            copyFile(source, backupFile);

            // 然后压缩 .bak 文件
            try (
                    FileInputStream fis = new FileInputStream(backupFile);
                    FileOutputStream fos = new FileOutputStream(compressedFilePath);
                    GZIPOutputStream gzipOS = new GZIPOutputStream(fos)
            ) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    gzipOS.write(buffer, 0, len);
                }
                gzipOS.finish();
            }

            // 删除 .bak 文件
            if (!backupFile.delete()) {
                System.err.println("Failed to delete backup file: " + backupFile.getAbsolutePath());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 实现文件复制功能
    private static void copyFile(File source, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(source);
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }
}