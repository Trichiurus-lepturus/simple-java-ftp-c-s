package edu.lepturus.ftp.server;

import edu.lepturus.ftp.utils.SessionInfo;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 服务器会话类
 *
 * @author T.lepturus
 * @version 1.0
 */
public class ServerSession implements Runnable {
    private final Socket tcpSocket;
    private final BufferedReader tcpIn;
    private final BufferedWriter tcpOut;
    private static final int TIMEOUT = 5 * 60 * 1000;
    private final SessionInfo sessionInfo;
    private final Navigator navigator;

    private final Object lock = new Object();
    private static final long DELTA = 1;

    public ServerSession(Socket tcpSocket,
                         BufferedReader tcpIn,
                         BufferedWriter tcpOut,
                         SessionInfo sessionInfo,
                         Path ROOT) throws SocketException {
        this.tcpSocket = tcpSocket;
        this.tcpIn = tcpIn;
        this.tcpOut = tcpOut;
        this.tcpSocket.setSoTimeout(TIMEOUT);
        this.sessionInfo = sessionInfo;
        this.navigator = new Navigator(ROOT);
    }

    @Override
    public void run() {
        try {
            CommandHandler commandHandler = new CommandHandler(
                    navigator,
                    sessionInfo,
                    tcpOut,
                    sessionInfo.getClientAddress(),
                    sessionInfo.getClientUdpPort(),
                    sessionInfo.getFILE_UDP_PACKET_SIZE());
            tcpOut.write(FileSystems.getDefault().getSeparator() + navigator.getPwd());
            tcpOut.newLine();
            tcpOut.flush();
            String line;
            while ((line = tcpIn.readLine()) != null) {
                System.out.println("Session " + sessionInfo.getUuid() + ", TCP IN: " + line);
                if (line.trim().equals("bye")) {
                    tcpOut.write("Bye for now!");
                    tcpOut.newLine();
                    tcpOut.flush();
                    break;
                } else {
                    List<String> args = parse(line.trim(), tcpIn);
                    try {
                        commandHandler.handle(args);
                    } catch (CommandHandler.NoSuchCommandException | CommandHandler.ArgumentCountException e) {
                        tcpOut.write(e.getMessage());
                        tcpOut.newLine();
                        tcpOut.flush();
                    } finally {
                        tcpOut.write(FileSystems.getDefault().getSeparator() + navigator.getPwd());
                        tcpOut.newLine();
                        tcpOut.flush();
                    }
                }
                synchronized (lock) {
                    try {
                        lock.wait(DELTA);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } finally {
            try {
                System.out.println("Closing Session: uuid " + sessionInfo.getUuid());
                tcpIn.close();
                tcpOut.close();
                tcpSocket.close();
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    /**
     * 解析命令字符串为字元组。
     * 允许折行，遇行尾反斜杠会尝试读取新行，若新行非空则继续解析；
     * 空格分隔参数，但可以使用引号将包含空格的参数包围起来；
     * 在单引号内反斜杠转义单引号，双引号内反斜杠转义双引号，
     * 引号内双反斜杠表示反斜杠。
     *
     * @param line 命令字符串
     * @param in BufferedReader
     * @return 解析结果
     * @throws IOException 解析过程中遇到的IO异常
     */
    private List<String> parse(String line, BufferedReader in) throws IOException {
        List<String> list = new ArrayList<>();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        StringBuilder argBuilder = new StringBuilder();
        StringBuilder lineBuilder = new StringBuilder(line);
        for (int i = 0; i < lineBuilder.length(); ++i) {
            switch (lineBuilder.charAt(i)) {
                case '\\': {
                    if (i == lineBuilder.length() - 1) {
                        String nextLine = in.readLine();
                        if (nextLine != null && !nextLine.trim().isEmpty()) {
                            lineBuilder.append(nextLine.trim());
                        } else {
                            argBuilder.append('\\');
                        }
                    } else if ((inSingleQuote || inDoubleQuote)
                            && i < lineBuilder.length() - 1
                            && lineBuilder.charAt(i + 1) == '\\') {
                        argBuilder.append('\\');
                        ++i;
                    } else if (inSingleQuote
                            && i < lineBuilder.length() - 1
                            && lineBuilder.charAt(i + 1) == '\'') {
                        argBuilder.append('\'');
                        ++i;
                    } else if (inDoubleQuote
                            && i < lineBuilder.length() - 1
                            && lineBuilder.charAt(i + 1) == '\"') {
                        argBuilder.append('\"');
                        ++i;
                    } else {
                        argBuilder.append('\\');
                    }
                    break;
                }
                case '\'': {
                    if (!inDoubleQuote) {
                        inSingleQuote = !inSingleQuote;
                    } else {
                        argBuilder.append('\'');
                    }
                    break;
                }
                case '\"': {
                    if (!inSingleQuote) {
                        inDoubleQuote = !inDoubleQuote;
                    } else {
                        argBuilder.append('\"');
                    }
                    break;
                }
                case ' ': {
                    if (inSingleQuote || inDoubleQuote) {
                        argBuilder.append(' ');
                    } else {
                        if (!argBuilder.toString().trim().isEmpty()) {
                            list.add(argBuilder.toString());
                        }
                        argBuilder = new StringBuilder();
                    }
                    break;
                }
                default: {
                    argBuilder.append(lineBuilder.charAt(i));
                    break;
                }
            }
        }
        if (!argBuilder.toString().trim().isEmpty()) {
            list.add(argBuilder.toString());
        }
        return list;
    }
}
