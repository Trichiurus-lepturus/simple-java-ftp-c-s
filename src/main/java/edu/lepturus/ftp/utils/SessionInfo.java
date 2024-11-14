package edu.lepturus.ftp.utils;

import java.io.*;
import java.net.InetAddress;
import java.util.Objects;
import java.util.UUID;

import jakarta.json.bind.*;
import jakarta.json.bind.annotation.JsonbTypeAdapter;

/**
 * 会话信息类
 *
 * @author T.lepturus
 * @version 1.0
 */
public class SessionInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID uuid;

    @JsonbTypeAdapter(InetAddressJsonAdapter.class)
    private InetAddress clientAddress;

    private int clientTcpPort;
    private int clientUdpPort;

    private int FILE_UDP_PACKET_SIZE;

    /**
     * 为了能成功调用Jsonb序列化为JSON
     */
    public SessionInfo() {
    }

    public SessionInfo(UUID uuid, InetAddress clientAddress, int clientTcpPort, int clientUdpPort, int FILE_UDP_PACKET_SIZE) {
        this.uuid = uuid;
        this.clientAddress = clientAddress;
        this.clientTcpPort = clientTcpPort;
        this.clientUdpPort = clientUdpPort;
        this.FILE_UDP_PACKET_SIZE = FILE_UDP_PACKET_SIZE;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public InetAddress getClientAddress() {
        return clientAddress;
    }

    public void setClientAddress(InetAddress clientAddress) {
        this.clientAddress = clientAddress;
    }

    public int getClientTcpPort() {
        return clientTcpPort;
    }

    public void setClientTcpPort(int clientTcpPort) {
        this.clientTcpPort = clientTcpPort;
    }

    public int getClientUdpPort() {
        return clientUdpPort;
    }

    public void setClientUdpPort(int clientUdpPort) {
        this.clientUdpPort = clientUdpPort;
    }

    public int getFILE_UDP_PACKET_SIZE() {
        return FILE_UDP_PACKET_SIZE;
    }

    public void setFILE_UDP_PACKET_SIZE(int FILE_UDP_PACKET_SIZE) {
        this.FILE_UDP_PACKET_SIZE = FILE_UDP_PACKET_SIZE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionInfo that = (SessionInfo) o;
        return Objects.equals(getUuid(), that.getUuid());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getUuid());
    }

    /**
     * 序列化为JSON字符串
     *
     * @param sessionInfo 会话信息对象
     * @return JSON String
     * @throws Exception Exception
     */
    public static String sessionInfoToJsonString(SessionInfo sessionInfo) throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.toJson(sessionInfo);
        }
    }

    /**
     * 反序列化从JSON字符串
     *
     * @param jsonString JSON String
     * @return 会话信息对象
     * @throws Exception Exception
     */
    public static SessionInfo jsonStringToSessionInfo(String jsonString) throws Exception {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.fromJson(jsonString, SessionInfo.class);
        }
    }
}
