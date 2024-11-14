package edu.lepturus.ftp.utils;

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 提供将InetAddress和JSON互转功能的工具类
 *
 * @author T.lepturus
 * @version 1.0
 */
public class InetAddressJsonAdapter implements JsonbAdapter<InetAddress, String> {

    @Override
    public String adaptToJson(InetAddress obj) {
        if (obj == null) {
            return null;
        }
        return obj.getHostAddress();
    }

    @Override
    public InetAddress adaptFromJson(String obj) {
        if (obj == null || obj.isEmpty()) {
            return null;
        }
        try {
            return InetAddress.getByName(obj);
        } catch (UnknownHostException e) {
            throw new JsonbException("Failed to deserialize InetAddress", e);
        }
    }
}