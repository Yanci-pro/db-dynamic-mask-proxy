package com.proxy;

import com.proxy.common.ProxyServer;
import com.proxy.constants.DBTypeEnum;
import com.proxy.model.ProxyConfig;

/**
 * @description: some desc
 * @author: yx
 * @date: 2021/12/8 10:12
 */
public class App {
    public static void main(String[] args) {
        ProxyConfig oracleConfig = new ProxyConfig();
        oracleConfig.setRemoteAddr("172.16.*.*");
        oracleConfig.setServerPort(6003);
        oracleConfig.setRemotePort(1521);
        oracleConfig.setDbType(DBTypeEnum.oracle);

//        config.setRemoteaddr("172.16.*.*");
//        config.setRemotePort(1521);
////        config.setRemoteaddr("127.0.0.1");

//        config.setRemotePort(7777);
//
////        config.setRemoteaddr("172.16.123.50");
//        config.setRemoteaddr("172.16.120.114");

        ProxyConfig sqlServerConfig = new ProxyConfig();
        sqlServerConfig.setRemoteAddr("172.16.*.*");
        sqlServerConfig.setRemotePort(1433);
        sqlServerConfig.setServerPort(6003);
        sqlServerConfig.setDbType(DBTypeEnum.sqlserver);

        ProxyConfig postGrepSqlConfig = new ProxyConfig();
        postGrepSqlConfig.setRemoteAddr("172.16.*.*");
        postGrepSqlConfig.setRemotePort(5432);
        postGrepSqlConfig.setServerPort(6004);
        postGrepSqlConfig.setDbType(DBTypeEnum.postgresql);

        ProxyConfig mySqlConfig = new ProxyConfig();
        mySqlConfig.setRemoteAddr("172.16.121.134");
        mySqlConfig.setRemotePort(3306);
        mySqlConfig.setServerPort(6001);
        mySqlConfig.setDbType(DBTypeEnum.mysql);
        new ProxyServer(oracleConfig).init();
    }
}
