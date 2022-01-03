package com.proxy.sqlparser;

import com.proxy.model.ProxyConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.apache.commons.lang.StringUtils;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
public class PostGrepSqlParser extends DefaultSqlParser {
    String charset = "utf8";
    String rule = "concat(SUBSTR(#field#,1,CHAR_LENGTH(#field#)/2),substr('*************',CHAR_LENGTH(#field#)/2,CHAR_LENGTH(#field#)/2)) as #field#";

    public void dealChannel(ProxyConfig config, Channel channel, Object msg) {
        ByteBuf readBuffer = (ByteBuf) msg;
        int oldByteLength = readBuffer.readableBytes();
        InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
        String hostString = remoteAddress.getHostString();
        int remotePort = remoteAddress.getPort();
        if (Objects.equals(hostString, config.getRemoteAddr()) && Objects.equals(config.getRemotePort(), remotePort) && oldByteLength > 8) {
            //取第一位，如果是80表示从jdbc和idea来的请求，数据复杂一点，如果是81表示从navicat和psql的客户端来的请求，结构稍微简单点
            int startByte = readBuffer.getByte(0);
            switch (startByte) {
                case 80:
                    dealComplex(channel, readBuffer);
                    break;
                case 81:
                    dealSimple(channel, readBuffer);
                    break;
                default:
                    readBuffer.retain();
                    channel.writeAndFlush(readBuffer);
                    break;
            }
        } else {
            channel.writeAndFlush(readBuffer);

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
    void dealSimple(Channel channel, ByteBuf readBuffer) {
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
        if (oldSql.toLowerCase().startsWith("select") && (!oldSql.toLowerCase().contains("information_schema"))) {
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
        } catch (Exception e) {
            System.out.println("错误sql:" + sql);
        }
        return sql;

    }

    /**
     * 处理复杂关系的数据，指jdbc和idea连接的请求
     * 这种客户端有允许发送大于64字节的数据了，没心情研究为什么
     *
     * @param readBuffer
     */
    void dealComplex(Channel channel, ByteBuf readBuffer) {
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
        }
        channel.writeAndFlush(readBuffer);
    }
}
