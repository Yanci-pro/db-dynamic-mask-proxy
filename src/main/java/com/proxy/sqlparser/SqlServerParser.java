package com.proxy.sqlparser;

import com.proxy.model.ProxyConfig;
import com.proxy.util.PrintUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang.StringUtils;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @description: some desc
 * 核心代码1
 * SQLServerConnection.sendLogon
 * 核心代码2：最终输出代码的位置
 * TDSWriter.flush
 * 关键代码3：TDSWriter.writePacketHeader
 * 确定前面前面8位的字节关系
 * <p>
 * int var2 = this.stagingBuffer.position();
 * ++this.packetNum;
 * this.stagingBuffer.put(0, this.tdsMessageType); 消息类型，假设3位查询，暂不处理，假设不变
 * this.stagingBuffer.put(1, (byte)var1); 1| this.sendResetConnection，假设不变
 * this.stagingBuffer.put(2, (byte)(var2 >> 8 & 255)); var2 >> 8 & 255  var2表示字符串总长度
 * this.stagingBuffer.put(3, (byte)(var2 >> 0 & 255)); var2 >> 0 & 255  var2表示字符串总长度
 * this.stagingBuffer.put(4, (byte)(this.tdsChannel.getSPID() >> 8 & 255));预计是固定这，暂不处理
 * this.stagingBuffer.put(5, (byte)(this.tdsChannel.getSPID() >> 0 & 255));预计是固定这，暂不处理
 * this.stagingBuffer.put(6, (byte)(this.packetNum % 256));可能是数包的长度，暂不考虑
 * this.stagingBuffer.put(7, (byte)0);分隔符，固定值
 * <p>
 * <p>
 * 核心代码3：sql字符串写入缓冲区
 * SQLServerStatement.doExecuteCursored
 * @author: yx
 * @date: 2021/12/7 15:40
 * sql数据包格式：
 * 大概分成下面几个部分
 * 1.1-8位，如上所示，最后会根据数据包长度进行修改
 * 2.9-30位，SQLServerStatement.doExecuteCursored方法会调用代码
 * TDSWriter var3 = var1.startRequest((byte)3);
 * 创建一个对象，前面三十位已经确定了，前八位会最后修改
 * 3.1.如果是常规情况，如idea,dbeaver,navicat，sqlserver的的带客户端等连接工具和jdbc的增删改语句则第三部分直接从第30位开始就是字符的字节
 * 3.2.如果是jdbc的查询语句第三部分长27位（暂时是这个结果，待测试），这27位的写入代码在SQLServerStatement.doExecuteCursored方法
 * 4。如果走到3.2则第四部分预测从第57位开始读取时自己的字符串内容
 * 5.第五部分也是jdbc的查询语句才有的，代码也在SQLServerStatement.doExecuteCursored方法，暂时测试的长度是27位
 * <p>
 * 3.1的27位构成是，37-45是一个组别，与5的最后一张数据相同 var3.writeRPCInt(null, 0,true)
 * 5的27位是调用的 var3.writeRPCInt(String var1, Integer var2, boolean var3) 方法，每次9个字节，共计三组
 * <p>
 * 另外还需要注意第56位
 * 核心代码4
 * TDSWriter.writeRPCStringUnicode
 * 第56位的代码在这里，如果说
 * 第55位和第56位是表示长度得，写入得是short，如果字符大于8000则会表示int,写入四个字节。暂时不考虑int类型
 * <p>
 * <p>
 * 关键代码标志位
 */

public class SqlServerParser extends DefaultSqlParser {
    String rule = "concat(substring(#field#,1,LEN(#field#)/2),substring('*************',LEN(#field#)/2,LEN(#field#)/2)) as #field#";
    public static Charset defaultCharset = Charset.forName("gbk");

    @Override
    public void dealChannel(ChannelHandlerContext ctx, ProxyConfig config, Channel channel, Object msg) {

        ByteBuf readBuffer = (ByteBuf) msg;
        //如果是服务端发送的消息远程地址为空
        InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
        String hostString = remoteAddress.getHostString();
        int port = remoteAddress.getPort();
        //只有发送给数据库的数据才需要进行处理
        int readableBytes = readBuffer.readableBytes();


        if (Objects.equals(hostString, config.getRemoteAddr()) && Objects.equals(port, config.getRemotePort()) && readableBytes > 30) {
            byte[] bytes = new byte[readableBytes];
            readBuffer.getBytes(0, bytes);
            System.out.println("SQL=" + new String(bytes));
            PrintUtil.print(bytes);
            int startByte = readBuffer.getByte(0);
            //因为连接工具的各种交互关系存在很多的不确定性，所以暂时对报错的不处理，直接跳过，详细的情况后续再研究
            try {
                switch (startByte) {
                    //据观察大胆猜测第一位如果位3则表示是jdbc来的查询请求
                    case 3:
                        dealComplex(channel, readBuffer);
                        break;
                    case 1:
                        dealSimple(channel, readBuffer);
                        break;
                    default:
                        readBuffer.retain();
                        channel.writeAndFlush(readBuffer);
                        break;
                }
            } catch (Exception e) {
                readBuffer.retain();
                channel.writeAndFlush(readBuffer);

            }

        } else {
            channel.writeAndFlush(readBuffer);

        }
    }

    /**
     * 处理简单的客户端,除了jdbc的查询其他的都从这里处理
     * ，支持长度赞未测试，后续再处理
     *
     * @param readBuffer
     */
    void dealSimple(Channel channel, ByteBuf readBuffer) {
        int oldByteLength = readBuffer.readableBytes();
        byte[] headerBytes = new byte[30];
        readBuffer.getBytes(0, headerBytes);

//        //获取长度
        int byteLength = getByteLength(headerBytes);
        //这是是为了验证猜想
        if (byteLength != oldByteLength) {
            throw new RuntimeException("error");
        }
        int oldStrLength = oldByteLength - 30;
        int oldByteStart = 30;
        //排除其他请求
        if (oldStrLength < 0) {
            readBuffer.retain();
            channel.writeAndFlush(readBuffer);
            return;
        }
        byte[] oldSqlBytes = new byte[oldStrLength / 2];
        for (int i = 0; i < oldStrLength / 2; i++) {
            oldSqlBytes[i] = readBuffer.getByte(30 + i * 2);
        }
        String oldSql = new String(oldSqlBytes);
        if (oldSql.toLowerCase().contains("select") && oldSql.toLowerCase().contains("sms")) {
//            String newSql = oldSql;
            String newSql = replaceSql(oldSql);
            System.out.println("oldSql=" + oldSql);
            System.out.println("newSql=" + newSql);

            byte[] newSqlBytes = newSql.getBytes();
            int newSqlResultBytesLength = newSqlBytes.length * 2;
            byte[] newSqlResultBytes = new byte[newSqlResultBytesLength];
            for (int i = 0; i < newSqlBytes.length; i++) {
                newSqlResultBytes[i * 2] = newSqlBytes[i];
                newSqlResultBytes[i * 2 + 1] = 0;
            }
            int newByteLength = 30 + newSqlResultBytes.length;
            setHeaderBytes(newByteLength, headerBytes);
            readBuffer.writerIndex(0);
            readBuffer.writeBytes(headerBytes);
            readBuffer.writeBytes(newSqlResultBytes);

            int i = readBuffer.readableBytes();
            byte[] bytes = new byte[i];
            readBuffer.getBytes(0, bytes);
            System.out.println("replace-length=" + i);
            System.out.println("readsql=" + new String(bytes));
            PrintUtil.print(bytes);
            readBuffer.writerIndex(0);
            readBuffer.writeBytes(headerBytes);
            readBuffer.writeBytes(newSqlResultBytes);
        } else {
            readBuffer.retain();
        }

        channel.writeAndFlush(readBuffer);
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
            int form = sql.toLowerCase().indexOf("from");
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


    public static int getByteLength(byte[] data) {
        return ((data[2] & 0xff) << 8) + (data[3] & 0xff);
    }

    public static void setHeaderBytes(int length, byte[] data) {
        data[2] = (byte) (length >> 8 & 255);
        data[3] = (byte) (length >> 0 & 255);
    }


    /**
     * 处理复杂关系的数据，指jdbc的请求
     * 注意这种方式最大接收496字节
     *
     * @param readBuffer
     */
    void dealComplex(Channel channel, ByteBuf readBuffer) {
        int oldByteLength = readBuffer.readableBytes();
        byte[] headerBytes = new byte[57];
        readBuffer.getBytes(0, headerBytes);

//        //获取长度
        int byteLength = getByteLength(headerBytes);
        //这是是为了验证猜想
        if (byteLength != oldByteLength) {
            throw new RuntimeException("error");
        }
        int oldStrLength = oldByteLength - 57 - 27;
        int oldByteStart = 57;
        //排除其他请求
        if (oldStrLength < 0) {
            readBuffer.retain();
            channel.writeAndFlush(readBuffer);
            return;
        }
        byte[] oldSqlBytes = new byte[oldStrLength / 2];
        for (int i = 0; i < oldStrLength / 2; i++) {
            oldSqlBytes[i] = readBuffer.getByte(57 + i * 2);
        }
        byte[] oldEndBytes = new byte[27];
        readBuffer.getBytes(oldByteLength - 27, oldEndBytes);

        String oldSql = new String(oldSqlBytes);
//        String newSql = oldSql;
        String newSql = replaceSql(oldSql);
        System.out.println("oldSql=" + oldSql);
        System.out.println("newSql=" + newSql);

        byte[] newSqlBytes = newSql.getBytes();
        int newSqlResultBytesLength = newSqlBytes.length * 2;
        byte[] newSqlResultBytes = new byte[newSqlResultBytesLength];
        for (int i = 0; i < newSqlBytes.length; i++) {
            newSqlResultBytes[i * 2] = newSqlBytes[i];
            newSqlResultBytes[i * 2 + 1] = 0;
        }
        int newByteLength = 57 + 27 + newSqlResultBytes.length;
        setHeaderBytes(newByteLength, headerBytes);
        readBuffer.writerIndex(0);
        headerBytes[56] = (byte) (newSqlResultBytesLength >> 8 & 255);
        headerBytes[55] = (byte) (newSqlResultBytesLength >> 0 & 255);
        readBuffer.writeBytes(headerBytes);
        readBuffer.writeBytes(newSqlResultBytes);
        readBuffer.writeBytes(oldEndBytes);

        int i = readBuffer.readableBytes();
        byte[] bytes = new byte[i];
        readBuffer.getBytes(0, bytes);
        System.out.println("replace-length=" + i);
        System.out.println("readsql=" + new String(bytes));
        PrintUtil.print(bytes);
        readBuffer.writerIndex(0);
        readBuffer.writeBytes(headerBytes);
        readBuffer.writeBytes(newSqlResultBytes);
        readBuffer.writeBytes(oldEndBytes);
        channel.writeAndFlush(readBuffer);

    }

}
