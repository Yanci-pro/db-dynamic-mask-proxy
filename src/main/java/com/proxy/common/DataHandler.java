package com.proxy.common;

import com.proxy.model.ProxyConfig;
import com.proxy.sqlparser.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;


/**
 * @description: some desc
 * @author: yx
 * @date: 2021/12/6 16:21
 */
@ChannelHandler.Sharable
public class DataHandler extends ChannelHandlerAdapter {
    Channel channel;

    private DefaultSqlParser sqlParser;
    private ProxyConfig config;

    public DataHandler(Channel channel, ProxyConfig config) {
        this.channel = channel;
        this.config = config;
        switch (config.getDbType()) {
            case mysql:
                sqlParser = new MySqlParser();
                break;
            case postgresql:
                sqlParser = new PostGrepSqlParser();
                break;
            case oracle:
                sqlParser = new OracleParser();
                break;
            case sqlserver:
                sqlParser = new SqlServerParser();
                break;
            default:
                sqlParser = new DefaultSqlParser();
                break;
        }
    }

    /**
     * 业务处理逻辑
     * 用于处理读取数据请求的逻辑。
     * ctx - 上下文对象。其中包含于客户端建立连接的所有资源。 如： 对应的Channel
     * msg - 读取到的数据。 默认类型是ByteBuf，是Netty自定义的。是对ByteBuffer的封装。 因为要把读取到的数据写入另外一个通道所以必须要考虑缓冲区复位问题,不然会报错。
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        sqlParser.dealChannel(config,channel, msg);
    }

    /**
     * 异常处理逻辑， 当客户端异常退出的时候，也会运行。
     * ChannelHandlerContext关闭，也代表当前与客户端连接的资源关闭。
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("server exceptionCaught method run..." + cause);
        cause.printStackTrace();
        channel.closeFuture().sync();
        ctx.close();

    }


}
