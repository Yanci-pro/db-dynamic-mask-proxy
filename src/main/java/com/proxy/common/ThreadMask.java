package com.proxy.common;

import com.proxy.model.ProxyConfig;
import io.netty.channel.ChannelFuture;

/**
 * @description: some desc
 * @author: yx
 * @date: 2021/12/6 16:59
 */
public class ThreadMask extends Thread {
    ProxyConfig config;
    String threadName;

    public ThreadMask(ProxyConfig config, String threadName) {
        this.config = config;
        this.setName(threadName);
    }

    @Override
    public void run() {
        ProxyServer proxyServer = new ProxyServer(config);
        ChannelFuture init = proxyServer.init();
        try {
            init.sync();
            //注意这里必须写关闭channel的future为同步方法,因为只有主线程结束才会关闭他会一直阻塞在这里,不然当服务启动过后就会结束主线程,整个任务接结束了
            init.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                init.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
