package com.proxy.model;

import com.proxy.constants.DBTypeEnum;

/**
 * @description: some desc
 * @author: yx
 * @date: 2021/12/6 16:52
 */
public class ProxyConfig {
    private int serverPort;
    private String remoteAddr;
    private int remotePort;
    private DBTypeEnum dbType;

    public ProxyConfig() {
    }

    public ProxyConfig(int serverPort, String remoteAddr, int remotePort, DBTypeEnum dbType) {
        this.serverPort = serverPort;
        this.remoteAddr = remoteAddr;
        this.remotePort = remotePort;
        this.dbType = dbType;
    }

    public int getServerPort() {
        return serverPort;
    }

    public ProxyConfig setServerPort(int serverPort) {
        this.serverPort = serverPort;
        return this;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public ProxyConfig setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
        return this;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public ProxyConfig setRemotePort(int remotePort) {
        this.remotePort = remotePort;
        return this;
    }

    public DBTypeEnum getDbType() {
        return dbType;
    }

    public ProxyConfig setDbType(DBTypeEnum dbType) {
        this.dbType = dbType;
        return this;
    }
}
