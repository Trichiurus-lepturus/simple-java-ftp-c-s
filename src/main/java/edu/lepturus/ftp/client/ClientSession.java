package edu.lepturus.ftp.client;

import edu.lepturus.ftp.utils.FileInfo;

import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Scanner;

/**
 * 客户端会话类
 *
 * @author T.lepturus
 * @version 1.0
 */
public class ClientSession {
    private final Socket tcpSocket;
    private final BufferedReader tcpIn;
    private final BufferedWriter tcpOut;
    private final DatagramSocket udpSocket;
    private final int UDP_PACKET_SIZE;
    private final Path ROOT;

    private final Object runLock = new Object();
    private static final long RUN_DELTA = 1;

    private static final String PS = "> ";

    public ClientSession(
            Socket tcpSocket,
            BufferedReader tcpIn,
            BufferedWriter tcpOut,
            DatagramSocket udpSocket,
            int UDP_PACKET_SIZE,
            Path ROOT) throws SocketException {
        this.tcpSocket = tcpSocket;
        this.tcpSocket.setSoTimeout(0);
        this.tcpIn = tcpIn;
        this.tcpOut = tcpOut;
        this.udpSocket = udpSocket;
        this.udpSocket.setSoTimeout(0);
        this.UDP_PACKET_SIZE = UDP_PACKET_SIZE;
        this.ROOT = ROOT;
    }

    public void run() {
        try (Scanner scanner = new Scanner(System.in)) {
            String line = tcpIn.readLine();
            System.out.println(line);
            while (true) {
                System.out.print(PS);
                String userInput = scanner.nextLine();
                tcpOut.write(userInput);
                tcpOut.newLine();
                tcpOut.flush();
                if (userInput.trim().startsWith("get")) {
                    receiveFileOverUDP();
                }
                String response;
                do {
                    response = tcpIn.readLine();
                    System.out.println(response);
                } while (tcpIn.ready());
                if (userInput.trim().equals("bye")) {
                    break;
                }
                synchronized (runLock) {
                    try {
                        runLock.wait(RUN_DELTA);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } finally {
            try {
                udpSocket.close();
                tcpSocket.close();
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    /**
     * 使用UDP接收文件（不保证可靠性）
     */
    private void receiveFileOverUDP() {
        byte[] buffer = new byte[UDP_PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            udpSocket.setSoTimeout(3 * 1000);
            udpSocket.receive(packet);
            udpSocket.setSoTimeout(1000);
            FileInfo fileInfo = FileInfo.bytesToFileInfo(packet.getData());
            Path filePath = ROOT.resolve(fileInfo.getFileName());
            System.out.println("Start receiving file: " + fileInfo.getFileName());
            long totalPackets = fileInfo.getPacketCount();
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                long receivedPackets = 0;
                while (receivedPackets < totalPackets) {
                    udpSocket.receive(packet);
                    System.out.println("Received packet number: " + receivedPackets);
                    fos.write(packet.getData(),0, packet.getLength());
                    ++receivedPackets;
                }
                System.out.println("All packets received.");
            }
            String srcCrc32 = fileInfo.getCrc32();
            String dstCrc32 = FileInfo.crc32Calculator(filePath);
            if (Objects.equals(srcCrc32, dstCrc32)) {
                System.out.println("File transfer completed (CRC32 checked): " + ROOT.relativize(filePath));
            } else {
                System.out.println("Please try again. File corrupted: " + ROOT.relativize(filePath));
                System.out.println("src: " + srcCrc32);
                System.out.println("dst: " + dstCrc32);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println(e.getMessage());
        } finally {
            try {
                udpSocket.setSoTimeout(0);
            } catch (SocketException e) {
                System.err.println(e.getMessage());
            }
        }
    }
}
