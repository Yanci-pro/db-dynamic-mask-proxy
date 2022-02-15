package com.proxy.sqlparser;

import com.proxy.model.ProxyConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

/**
 * @description: 默认的处理方式，不做任何处理
 * @author: yx
 * @date: 2021/12/8 10:20
 *
 * <p>
 */
@Slf4j
public class DefaultSqlParser {

    //默认处理方式，对任何数据都不做处理，直接转发
    public void dealChannel(ChannelHandlerContext ctx, ProxyConfig config, Channel channel, Object msg) {
        channel.writeAndFlush(msg);
    }

    /**
     * 可以对删除语句自行做控制，这里只做日志记录
     *
     * @param ctx
     * @param config
     * @param channel
     * @param sql
     */
    void delete(ChannelHandlerContext ctx, ProxyConfig config, Channel channel, String sql) {
        InetSocketAddress inetSocketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        log.info("{}主机在{}上执行了删除语句：{}", inetSocketAddress.getAddress(), config.getRemoteAddr(), sql);
    }

    /**
     * 可以对新增语句自行做权限控制或拦截，这里只做日志记录
     *
     * @param ctx
     * @param config
     * @param channel
     * @param sql
     */
    void insert(ChannelHandlerContext ctx, ProxyConfig config, Channel channel, String sql) {
        InetSocketAddress inetSocketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        log.info("{}主机在{}上执行了新增语句：{}", inetSocketAddress.getAddress(), config.getRemoteAddr(), sql);
    }

    /**
     * 可以对修改语句自行做控制，检验或拦截，这里只做日志记录
     *
     * @param ctx
     * @param config
     * @param channel
     * @param sql
     */
    void update(ChannelHandlerContext ctx, ProxyConfig config, Channel channel, String sql) {
        InetSocketAddress inetSocketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        log.info("{}主机在{}上执行了修改语句：{}", inetSocketAddress.getAddress(), config.getRemoteAddr(), sql);
    }


}
