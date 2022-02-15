package com.proxy;

import com.proxy.common.ProxyServer;
import com.proxy.constants.DBTypeEnum;
import com.proxy.model.ProxyConfig;

import java.io.InputStream;
import java.util.*;

/**
 * @description: some desc
 * @author: yx
 * @date: 2021/12/8 10:12
 */
public class App {
    public static void main(String[] args) {
        for (ProxyConfig proxyConfig : readFile("/config.properties")) {
            new ProxyServer(proxyConfig).init();
        }
    }

    /**
     * 获取配置文件
     *
     * @param name
     * @return
     */
    public static List<ProxyConfig> readFile(String name) {
        InputStream in = App.class.getResourceAsStream(name);
        Properties properties = new Properties();
        // 使用List来存储每行读取到的字符串
        List<ProxyConfig> list = new ArrayList<>();
        try {
            properties.load(in);
            Set<Map.Entry<Object, Object>> entries = properties.entrySet();
            entries.forEach(item -> {
                String value = String.valueOf(item.getValue());
                String[] split = value.split(",");
                String remoteAddr = split[0];
                Integer remotePort = Integer.valueOf(split[1]);
                Integer serverPort = Integer.valueOf(split[2]);
                DBTypeEnum dbType = DBTypeEnum.valueOf(split[3]);
                list.add(new ProxyConfig(serverPort, remoteAddr, remotePort, dbType));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

}
