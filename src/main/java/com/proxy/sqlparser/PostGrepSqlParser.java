package com.proxy.sqlparser;

import com.proxy.model.ProxyConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.net.InetSocketAddress;
import java.util.*;

/**
 * @description: some desc
 * @author: yx
 * @date: 2021/12/7 15:40
 * 关键代码在这里，把sql写入buffer并设置头
 * QueryExecutorImpl.sendParse
 * postgrepsql的数据包自带的简化格式版最大长度为64字节，而且服务段接收也是最大长度为64字节
 * 暂时没有处理中文格式问题，后续测试再处理
 * 现阶注意只支持具体sql语句，不支持*号的脱敏
 */
@Slf4j
public class PostGrepSqlParser extends DefaultSqlParser {
    String charset = "utf8";
    String rule = "concat(SUBSTR(#field#,1,CHAR_LENGTH(#field#)/2),substr('*************',CHAR_LENGTH(#field#)/2,CHAR_LENGTH(#field#)/2)) as #field#";
    Map<String, ByteBuf> bufferMap = new HashMap();
    Set<String> isWait = new HashSet<>();

    @Override
    public void dealChannel(ChannelHandlerContext ctx, ProxyConfig config, Channel channel, Object msg) {
        ByteBuf readBuffer = (ByteBuf) msg;
        int oldByteLength = readBuffer.readableBytes();
        InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
        String hostString = remoteAddress.getHostString();
        int remotePort = remoteAddress.getPort();
        if (Objects.equals(hostString, config.getRemoteAddr()) && Objects.equals(config.getRemotePort(), remotePort) && oldByteLength > 8) {
            dealType(ctx, config, channel, msg);
        } else {
            channel.writeAndFlush(readBuffer);
        }
    }

    public void dealType(ChannelHandlerContext ctx, ProxyConfig config, Channel channel, Object msg) {
        ByteBuf readBuffer = (ByteBuf) msg;
        //首先第一步看看第一位是不是80或81
        byte startByte = readBuffer.getByte(0);
        String localPid = channel.localAddress().toString();
        if (startByte == 80 || startByte == 81 || isWait.contains(localPid)) {
            //如果是服务端发送的消息远程地址为空
            //只有发送给数据库的数据才需要进行处理
            int readableBytes = readBuffer.readableBytes();
            //第一步先获取会话的id,如果当前会话的pid没有被结束则直接把所有的数据写入到缓冲区buffer里面
            if (bufferMap.containsKey(localPid)) {
                ByteBuf byteBuf = bufferMap.get(localPid);
                //如果写入完全了则直接进行sql解析
                int index = readBuffer.writerIndex();
                byte[] headerBytes = new byte[4];
                byteBuf.getBytes(1, headerBytes);
                //获取长度
                byte[] tmpBytes = new byte[index];
                readBuffer.getBytes(0, tmpBytes);
                byteBuf.writeBytes(tmpBytes);
                int readableBytesNew = byteBuf.readableBytes();
                if (readableBytesNew >= byteBuf.writerIndex()) {
                    dealBytes(ctx, config, channel, byteBuf);
                    isWait.remove(localPid);
                    bufferMap.remove(localPid);

                }
            } else {
                //取第一位，如果是80表示从jdbc和idea来的请求，数据复杂一点，如果是81表示从navicat和psql的客户端来的请求，结构稍微简单点
                byte aByte = readBuffer.getByte(0);
                byte[] headerBytes = new byte[4];
                readBuffer.getBytes(1, headerBytes);
                //获取长度
                int byteLength = getByteLength(headerBytes);
                if (readableBytes >= byteLength) {
                    dealBytes(ctx, config, channel, readBuffer);
                } else {
                    //说明数据包不完全，先继续接收数据包等接收完全后再处理sql
                    ByteBuf tmpBuffer = Unpooled.buffer(byteLength);
                    tmpBuffer.writeBytes(readBuffer);
                    bufferMap.put(localPid, tmpBuffer);
                    isWait.add(localPid);
                }
            }
        } else {
            readBuffer.retain();
            channel.writeAndFlush(readBuffer);
        }
    }

    public void dealBytes(ChannelHandlerContext ctx, ProxyConfig config, Channel channel, ByteBuf readBuffer) {
        int startByte = readBuffer.getByte(0);
        switch (startByte) {
            case 80:
                dealComplex(ctx, config, channel, readBuffer);
                break;
            case 81:
                dealSimple(ctx, config, channel, readBuffer);
                break;
            default:
                readBuffer.retain();
                channel.writeAndFlush(readBuffer);
                break;
        }
    }


    /**
     * 根据byte数组得到字符串长度
     *
     * @param data
     * @return
     */

    public static int getByteLength(byte[] data) {
        int result = 0;
        for (int i = 0; i < data.length; i++) {
            result += (data[i] & 0xff) << ((3 - i) * 8);
        }
        return result;
    }

    /**
     * 根据字符串长度去生成数组中的信息
     *
     * @param length
     * @param data
     */
    public static void setHeaderBytes(int length, byte[] data) {
        data[0] = (byte) (length >>> 24);
        data[1] = (byte) (length >>> 16);
        data[2] = (byte) (length >>> 8);
        data[3] = (byte) length;
    }

    /**
     * 处理简单的客户端，指navicat和psql客户端发送的请求
     * 先处理假设sql最长只有64个字节，长的后续再处理
     *
     * @param readBuffer
     */
    void dealSimple(ChannelHandlerContext ctx, ProxyConfig config, Channel channel, ByteBuf readBuffer) {
        int oldByteLength = readBuffer.readableBytes();
        byte headByte = readBuffer.getByte(0);
        byte[] headerBytes = new byte[4];
        readBuffer.getBytes(1, headerBytes);
        //获取长度
        int byteLength = getByteLength(headerBytes);
        //读取数据
        byte[] oldSqlBytes = new byte[byteLength - 5];
        readBuffer.getBytes(5, oldSqlBytes);
        String oldSql = new String(oldSqlBytes);
        readBuffer.retain();
        if (oldSql.toLowerCase().startsWith("select") && (!oldSql.toLowerCase().contains("information_schema")) && (!oldSql.contains("*"))) {
            String newSql = replaceSql(oldSql);
            byte[] newSqlBytes = newSql.getBytes();
            setHeaderBytes(newSqlBytes.length + 5, headerBytes);
            readBuffer.writerIndex(0);
            readBuffer.writeByte(headByte);
            readBuffer.writeBytes(headerBytes);
            //这种数据包格式的服务端一次只能接收64个字节的包，比较恶心需要分多次发送
            //这里有很大优化空间，重心现在放在解析数据包上暂不处理，后续再优化
            for (int i = 0; i < newSqlBytes.length; i++) {
                readBuffer.writeByte(newSqlBytes[i]);
                int index = readBuffer.writerIndex();
                if (index == 64) {
                    channel.writeAndFlush(readBuffer);
                    readBuffer = Unpooled.buffer(64);
                }

            }
            //注意这里的结束位不能省略
            readBuffer.writeByte(0);
            channel.writeAndFlush(readBuffer);

        } else if (oldSql.toUpperCase(Locale.ROOT).startsWith("DELETE")) {
            delete(ctx, config, channel, oldSql);
            readBuffer.retain();
            channel.writeAndFlush(readBuffer);
        } else if (oldSql.toUpperCase(Locale.ROOT).startsWith("UPDATE")) {
            update(ctx, config, channel, oldSql);
            readBuffer.retain();
            channel.writeAndFlush(readBuffer);
        } else if (oldSql.toUpperCase(Locale.ROOT).startsWith("INSERT")) {
            insert(ctx, config, channel, oldSql);
            readBuffer.retain();
            channel.writeAndFlush(readBuffer);
        } else {
            readBuffer.retain();
            channel.writeAndFlush(readBuffer);
        }

    }

    /**
     * 解析sql进行替换
     *
     * @param sql
     * @return
     */
    public String replaceSql(String sql) {
        //这里有可能会出现select version等情况的sql,后续再处理，也可能就不会走到这里来先忽略
        try {
            int select = sql.toLowerCase().indexOf("select");
            int form = sql.indexOf("from");
            String substring = sql.substring(select, form);
            String[] split = substring.split(",");
            List<String> list = new ArrayList<>();
            for (String s : split) {
                String select1 = s.replace("select", "");
                list.add(rule.replace("#field#", select1));
            }
            String join = StringUtils.join(list, ",");
            sql = "select" + " " + join + " " + sql.substring(form);
            log.info("执行完sql替换即将执行的sql为:{}", sql);
        } catch (Exception e) {
            log.debug("替换sql失败,原sql为:{}", sql);
        }
        return sql;

    }

    /**
     * 处理复杂关系的数据，指jdbc和idea连接的请求
     * 这种客户端有允许发送大于64字节的数据了，没心情研究为什么
     *
     * @param readBuffer
     */
    void dealComplex(ChannelHandlerContext ctx, ProxyConfig config, Channel channel, ByteBuf readBuffer) {
        int oldByteLength = readBuffer.readableBytes();

        byte headByte = readBuffer.getByte(0);
        byte[] headerBytes = new byte[4];
        readBuffer.getBytes(1, headerBytes);
        //获取长度
        int byteLength = getByteLength(headerBytes);
        //读取数据
        byte[] oldSqlBytes = new byte[byteLength - 8];
        readBuffer.getBytes(6, oldSqlBytes);
        String oldSql = new String(oldSqlBytes);
        byte[] endBytes = new byte[oldByteLength - byteLength + 8 - 6];
        readBuffer.getBytes(byteLength - 8 + 6, endBytes);
        readBuffer.retain();
        if (oldSql.toLowerCase().contains("select") && (!oldSql.toLowerCase().contains("information_schema"))) {
            String newSql = replaceSql(oldSql);
            byte[] newSqlBytes = newSql.getBytes();
            setHeaderBytes(newSqlBytes.length + 8, headerBytes);
            readBuffer.writerIndex(0);
            readBuffer.writeByte(headByte);
            readBuffer.writeBytes(headerBytes);
            readBuffer.writeByte(0);
            readBuffer.writeBytes(newSqlBytes);
            readBuffer.writeBytes(endBytes);
        } else if (oldSql.toUpperCase(Locale.ROOT).startsWith("DELETE")) {
            delete(ctx, config, channel, oldSql);
        } else if (oldSql.toUpperCase(Locale.ROOT).startsWith("UPDATE")) {
            update(ctx, config, channel, oldSql);
        } else if (oldSql.toUpperCase(Locale.ROOT).startsWith("INSERT")) {
            insert(ctx, config, channel, oldSql);
        }
        readBuffer.readerIndex(0);
        channel.writeAndFlush(readBuffer);
    }
}
