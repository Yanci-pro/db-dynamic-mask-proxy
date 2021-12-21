package com.proxy.common;

import com.proxy.model.ProxyConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * @description: 创建代理service
 * @author: yx
 * @date: 2021/12/6 16:20
 */
public class ProxyServer {
    ServerBootstrap serverBootstrap;
    Bootstrap bootstrap;
    EventLoopGroup bossgroup;
    EventLoopGroup workgroup;
    ProxyConfig config;

    public ProxyServer(ProxyConfig config) {
        serverBootstrap = new ServerBootstrap();
        bootstrap = new Bootstrap();
        bossgroup = new NioEventLoopGroup();
        workgroup = new NioEventLoopGroup();
        serverBootstrap.group(bossgroup, workgroup);
        serverBootstrap.channel(NioServerSocketChannel.class);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.group(bossgroup);
        //下面的代码为缓冲区设置,其实是非必要代码,可以不用设置,也可以根据自己需求设置参数
        serverBootstrap.option(ChannelOption.SO_BACKLOG, 1024);
        // SO_SNDBUF发送缓冲区，SO_RCVBUF接收缓冲区，SO_KEEPALIVE开启心跳监测（保证连接有效）
        serverBootstrap.option(ChannelOption.SO_SNDBUF, 16 * 1024)
                .option(ChannelOption.SO_RCVBUF, 16 * 1024)
                .option(ChannelOption.SO_KEEPALIVE, true);
        this.config = config;
    }


    public ChannelFuture init() {
        serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel cliCh) throws Exception {
                        cliCh.pipeline().addLast(new DataHandler(ch, config));
                    }
                });
                ChannelFuture sync = bootstrap.connect(config.getRemoteAddr(), config.getRemotePort()).sync();
                ch.pipeline().addLast(new DataHandler(sync.channel(),config));
            }
        });
        ChannelFuture future = serverBootstrap.bind(config.getServerPort());
        return future;
    }
}
