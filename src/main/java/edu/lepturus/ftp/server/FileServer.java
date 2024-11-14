package edu.lepturus.ftp.server;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;

import edu.lepturus.ftp.utils.SessionInfo;

/**
 * 服务器类
 *
 * @author T.lepturus
 * @version 1.0
 */
public class FileServer {
    private static final int SERVER_TCP_PORT = 2020;

    private final ExecutorService executorService;
    private static final int POOL_SIZE = 4;

    private final ServerSocket tcpSocket;

    private final Path ROOT;

    private final Object lock = new Object();
    private static final long DELTA = 1;

    public FileServer(int TCP_LISTEN_PORT, Path ROOT) throws IOException {
        this.tcpSocket = new ServerSocket(TCP_LISTEN_PORT);
        this.ROOT = ROOT;
        this.executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * POOL_SIZE);
    }

    public ServerSocket getTcpSocket() {
        return tcpSocket;
    }

    public void run() {
        while (true) {
            try {
                Socket clientTcp = tcpSocket.accept();
                BufferedReader tcpIn = new BufferedReader(new InputStreamReader(clientTcp.getInputStream()));
                BufferedWriter tcpOut = new BufferedWriter(new OutputStreamWriter(clientTcp.getOutputStream()));
                SessionInfo sessionInfo = SessionInfo.jsonStringToSessionInfo(tcpIn.readLine().trim());
                String msg = "Session establishing: address "
                        + sessionInfo.getClientAddress()
                        + ", tcp port "
                        + sessionInfo.getClientTcpPort()
                        + ", udp port "
                        + sessionInfo.getClientUdpPort()
                        + ", uuid "
                        + sessionInfo.getUuid();
                System.out.println(msg);
                tcpOut.write(msg);
                tcpOut.newLine();
                tcpOut.flush();
                executorService.execute(new ServerSession(
                        clientTcp,
                        tcpIn,
                        tcpOut,
                        sessionInfo,
                        ROOT));
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
            synchronized (lock) {
                try {
                    lock.wait(DELTA);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * args[0]: 服务器根目录（绝对路径）
     *
     * @param args args
     */
    public static void main(String[] args) {
        try {
            Path root = Paths.get(args[0]);
            if (Files.exists(root) && Files.isDirectory(root)) {
                FileServer fileServer = new FileServer(SERVER_TCP_PORT, root);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("Shutdown Hook is running. Cleaning up resources...");
                    try {
                        fileServer.getTcpSocket().close();
                    } catch (IOException e) {
                        System.err.println(e.getMessage());
                    }
                }));
                fileServer.run();
            } else {
                System.err.println("This is not a valid root path!");
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}
