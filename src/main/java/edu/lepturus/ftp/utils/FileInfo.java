package edu.lepturus.ftp.utils;

import java.io.*;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * 文件信息类
 *
 * @author T.lepturus
 * @version 1.0
 */
public class FileInfo implements Serializable {
    private final String fileName;
    private final long packetCount;
    private final String crc32;

    public FileInfo(String fileName, long packetCount, String crc32) {
        this.fileName = fileName;
        this.packetCount = packetCount;
        this.crc32 = crc32;
    }

    public String getFileName() {
        return fileName;
    }

    public long getPacketCount() {
        return packetCount;
    }

    public String getCrc32() {
        return crc32;
    }

    /**
     * 序列化为byte[]
     *
     * @param fileInfo 文件信息对象
     * @return byte[]
     * @throws IOException IOException
     */
    public static byte[] fileInfoToBytes(FileInfo fileInfo) throws IOException {

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(fileInfo);
            return bos.toByteArray();
        }
    }

    /**
     * 反序列化从byte[]
     *
     * @param bytes byte[]
     * @return 文件信息对象
     * @throws IOException IOException
     * @throws ClassNotFoundException ClassNotFoundException
     */
    public static FileInfo bytesToFileInfo(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return (FileInfo) ois.readObject();
        }
    }

    public static String crc32Calculator(Path file) throws IOException {
        CRC32 crc32 = new CRC32();
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) != -1) {
                crc32.update(buffer, 0, length);
            }
        }
        return Long.toHexString(crc32.getValue());
    }
}
