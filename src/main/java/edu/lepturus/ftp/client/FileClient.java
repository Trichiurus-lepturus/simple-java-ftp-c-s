package edu.lepturus.ftp.client;

import edu.lepturus.ftp.utils.SessionInfo;

import java.io.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 客户端类
 *
 * @author T.lepturus
 * @version 1.0
 */
public class FileClient {
    private static final String PARA_HOST_NAME = "127.0.0.1";
    private static final int PARA_HOST_PORT = 2020;
    private static final String PARA_CLIENT_NAME = "127.0.0.1";

    private final InetAddress HOST_ADDR;
    private final int HOST_PORT;

    private final InetAddress CLIENT_ADDR;

    private static final int FILE_UDP_PACKET_SIZE = 32768;

    private final Path ROOT;

    public FileClient(
            InetAddress HOST_ADDR,
            int HOST_PORT,
            InetAddress CLIENT_ADDR,
            Path ROOT) {
        this.HOST_ADDR = HOST_ADDR;
        this.HOST_PORT = HOST_PORT;
        this.CLIENT_ADDR = CLIENT_ADDR;
        this.ROOT = ROOT;
    }

    public void run() {
        try (Socket tcpSocket = new Socket(HOST_ADDR, HOST_PORT);
             BufferedReader tcpIn = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
             BufferedWriter tcpOut = new BufferedWriter(new OutputStreamWriter(tcpSocket.getOutputStream()));
             DatagramSocket udpSocket = new DatagramSocket(0)) {
            SessionInfo sessionInfo = new SessionInfo(
                    UUID.randomUUID(),
                    CLIENT_ADDR,
                    tcpSocket.getLocalPort(),
                    udpSocket.getLocalPort(),
                    FILE_UDP_PACKET_SIZE);
            tcpOut.write(SessionInfo.sessionInfoToJsonString(sessionInfo));
            tcpOut.newLine();
            tcpOut.flush();
            System.out.println(tcpIn.readLine());
            ClientSession clientSession = new ClientSession(
                    tcpSocket,
                    tcpIn,
                    tcpOut,
                    udpSocket,
                    FILE_UDP_PACKET_SIZE,
                    ROOT);
            clientSession.run();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * args[0]: 客户端根目录（文件下载目标）
     *
     * @param args args
     */
    public static void main(String[] args) {
        try {
            new FileClient(
                    InetAddress.getByName(PARA_HOST_NAME), PARA_HOST_PORT,
                    InetAddress.getByName(PARA_CLIENT_NAME),
                    Paths.get(args[0])).run();
        } catch (UnknownHostException e) {
            System.err.println(e.getMessage());
        }
    }
}
