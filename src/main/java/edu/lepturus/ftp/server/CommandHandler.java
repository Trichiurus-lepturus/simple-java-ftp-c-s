package edu.lepturus.ftp.server;

import edu.lepturus.ftp.utils.FileInfo;
import edu.lepturus.ftp.utils.SessionInfo;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * 命令处理类
 *
 * @author T.lepturus
 * @version 1.0
 */
public class CommandHandler {
    private final Navigator navigator;
    private final SessionInfo sessionInfo;

    private final BufferedWriter tcpOut;
    private final InetAddress CLIENT_ADDRESS;
    private final int CLIENT_UDP_PORT;
    private final int UDP_PACKET_SIZE;

    private final Object lock = new Object();
    private static final long DELTA = 200;

    /**
     * 异常“没有这个命令”
     */
    public static class NoSuchCommandException extends Exception {
        public NoSuchCommandException(String message) {
            super(message);
        }
    }

    /**
     * 异常“参数数量错误”
     */
    public static class ArgumentCountException extends Exception {
        public ArgumentCountException(String message) {
            super(message);
        }
    }

    public CommandHandler(Navigator navigator,
                          SessionInfo sessionInfo,
                          BufferedWriter tcpOut,
                          InetAddress CLIENT_ADDRESS,
                          int CLIENT_UDP_PORT,
                          int UDP_PACKET_SIZE) {
        this.navigator = navigator;
        this.sessionInfo = sessionInfo;
        this.tcpOut = tcpOut;
        this.CLIENT_ADDRESS = CLIENT_ADDRESS;
        this.CLIENT_UDP_PORT = CLIENT_UDP_PORT;
        this.UDP_PACKET_SIZE = UDP_PACKET_SIZE;
    }

    /**
     * 处理命令
     *
     * @param args 解析后的参数组
     * @throws NoSuchCommandException 没有这个命令
     * @throws ArgumentCountException 参数数量错误
     */
    public void handle(List<String> args) throws NoSuchCommandException, ArgumentCountException {
        if (args.size() > 2) {
            throw new ArgumentCountException("Too many arguments!");
        } else if (args.isEmpty()) {
            return;
        }
        switch (args.get(0)) {
            case "ls": {
                try {
                    List<Path> list = args.size() > 1
                            ? navigator.ls(Paths.get(args.get(1)))
                            : navigator.ls();
                    String[][] tokens = new String[list.size()][3];
                    for (int i = 0; i < list.size(); ++i) {
                        if (Files.isDirectory(list.get(i))) {
                            tokens[i][0] = "<dir>";
                        } else if (Files.isRegularFile(list.get(i))) {
                            tokens[i][0] = "<file>";
                        } else {
                            tokens[i][0] = "<other>";
                        }
                        tokens[i][1] = list.get(i).getFileName().toString();
                        tokens[i][2] = formatFileSize(Files.readAttributes(list.get(i),
                                BasicFileAttributes.class).size());
                    }
                    printStringMatrix(tokens, tcpOut);
                    tcpOut.flush();
                } catch (IOException e) {
                    try {
                        tcpOut.write(e.getMessage());
                    } catch (IOException ex) {
                        System.err.println(ex.getMessage());
                    }
                }
                break;
            }
            case "cd": {
                if (args.size() < 2) {
                    throw new ArgumentCountException("Too few arguments!");
                }
                try {
                    navigator.cd(Paths.get(args.get(1)));
                } catch (NoSuchFileException e) {
                    try {
                        tcpOut.write(e.getMessage());
                        tcpOut.flush();
                    } catch (IOException ex) {
                        System.err.println(ex.getMessage());
                    }
                }
                break;
            }
            case "get": {
                if (args.size() < 2) {
                    throw new ArgumentCountException("Too few arguments!");
                }
                sendFileOverUDP(args.get(1));
                break;
            }
            default: {
                throw new NoSuchCommandException("No such command: " + args.get(0));
            }
        }
    }

    /**
     * 将以字节为单位的文件大小化为B, KiB, MiB, GiB为单位的
     *
     * @param sizeInBytes 文件字节数
     * @return 文件大小字符串
     */
    private String formatFileSize(long sizeInBytes) {
        if (sizeInBytes < 1024) {
            return sizeInBytes + " B";
        } else if (sizeInBytes < 1024 * 1024) {
            return String.format("%.2f KiB", sizeInBytes / 1024.0);
        } else if (sizeInBytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MiB", sizeInBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GiB", sizeInBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 整齐输出字符串矩阵
     *
     * @param matrix 源字符串矩阵
     * @param out BufferedWriter
     * @throws IOException IOException
     */
    private void printStringMatrix(String[][] matrix, BufferedWriter out) throws IOException {
        int[] maxWidths = new int[matrix[0].length];
        for (int col = 0; col < matrix[0].length; ++col) {
            for (String[] row : matrix) {
                if (row[col].length() > maxWidths[col]) {
                    maxWidths[col] = row[col].length();
                }
            }
        }
        for (String[] row : matrix) {
            for (int col = 0; col < row.length; ++col) {
                String formatted = String.format("%-" + (maxWidths[col] + 1) + "s", row[col]);
                out.write(formatted);
            }
            out.newLine();
        }
    }

    /**
     * 使用UDP传输文件（不保证可靠性）
     *
     * @param filePath 文件路径字符串
     */
    private void sendFileOverUDP(String filePath) {
        try (DatagramSocket udpOut = new DatagramSocket()) {
            Path file = navigator.get(Paths.get(filePath));
            byte[] fileInfoBytes = FileInfo.fileInfoToBytes(
                    new FileInfo(
                            file.getFileName().toString(),
                            file.toFile().length() / UDP_PACKET_SIZE + 1,
                            FileInfo.crc32Calculator(file)));
            DatagramPacket fileInfoPacket = new DatagramPacket(
                    fileInfoBytes,
                    fileInfoBytes.length,
                    CLIENT_ADDRESS,
                    CLIENT_UDP_PORT);
            udpOut.send(fileInfoPacket);
            try (FileInputStream fis = new FileInputStream(file.toFile())) {
                byte[] buffer = new byte[UDP_PACKET_SIZE];
                DatagramPacket packet;
                int packetNum = 0, bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    packet = new DatagramPacket(buffer, bytesRead, CLIENT_ADDRESS, CLIENT_UDP_PORT);
                    udpOut.send(packet);
                    System.out.println("Session " + sessionInfo.getUuid() + ", UDP sent packet number: " + packetNum++);
                    synchronized (lock) {
                        try {
                            lock.wait(DELTA);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        } catch (IOException e) {
            try {
                tcpOut.write(e.getMessage());
                tcpOut.newLine();
                tcpOut.flush();
            } catch (IOException ex) {
                System.err.println(ex.getMessage());
            }
        }
    }
}
