package com.proxy.sqlparser;

import com.proxy.model.ProxyConfig;
import io.netty.channel.Channel;

/**
 * @description: 默认的处理方式，不做仍和处理
 * @author: yx
 * @date: 2021/12/8 10:20
 * <p>
 */
public class DefaultSqlParser {

    //默认处理方式，对任何数据都不做处理，直接转发
    public void dealChannel(ProxyConfig config, Channel channel, Object msg) {
        channel.writeAndFlush(msg);
    }
}
