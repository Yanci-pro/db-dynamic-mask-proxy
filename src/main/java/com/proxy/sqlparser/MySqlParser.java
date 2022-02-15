package com.proxy.sqlparser;

import com.proxy.constants.MySQLPacket;
import com.proxy.constants.ThreadPublic;
import com.proxy.model.ProxyConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.*;


/**
 * @description: some desc
 * @author: yx
 * https://github.com/MyCATApache/Mycat-Server.git
 * mysql的数据包格式:数据长度标识,前3位+分割为第四位固定为0,第五位表示操作动作(增删查改...)+最后一位据0结束符,
 * 据网上资料说明数据包最大长度为16M最后一位是存储下一个字节长度的,暂不验证,场景暂时用不到,不处理暂时不收影响
 * 前三位算法:1=字符长度与0xff(255)进行与运算,
 * 2=字符串长度右移8位
 * 3=字符串长度右移16位
 * Buffer.writeLongInt
 * byte[] b = this.byteBuffer;
 * b[this.position++] = (byte) (i & 0xff);
 * b[this.position++] = (byte) (i >>> 8);
 * b[this.position++] = (byte) (i >>> 16);
 * 另外还涉及到连接方的连接编码方式,会在创建连接的时候有个:SET NAMES utf8mb4类似的命令,截取出来就可以知道对方的编码是什么格式,默认是gbk
 * @date: 2021/12/7 15:40
 */
@Slf4j
public class MySqlParser extends DefaultSqlParser {
    String rule = "concat(SUBSTR(#field#,1,CHAR_LENGTH(#field#)/2),substr('*************',CHAR_LENGTH(#field#)/2,CHAR_LENGTH(#field#)/2)) as #field#";
    public static Charset defaultCharset = Charset.forName("gbk");
    Map<String, ByteBuf> bufferMap = new HashMap();

    public void dealChannel(ChannelHandlerContext ctx, ProxyConfig config, Channel channel, Object msg) {

        ByteBuf readBuffer = (ByteBuf) msg;
        //如果是服务端发送的消息远程地址为空
        InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
        String hostString = remoteAddress.getHostString();
        int port = remoteAddress.getPort();
        //只有发送给数据库的数据才需要进行处理
        int readableBytes = readBuffer.readableBytes();
        if (hostString.equals(config.getRemoteAddr()) && Objects.equals(port, config.getRemotePort())) {
            //第一步先获取会话的id,如果当前会话的pid没有被结束则直接把所有的数据写入到缓冲区buffer里面
            String localPid = channel.localAddress().toString();
            if (bufferMap.containsKey(localPid)) {
                ByteBuf byteBuf = bufferMap.get(localPid);
                //如果写入完全了则直接进行sql解析
                int index = readBuffer.writerIndex();
                byte[] tmpBytes = new byte[index];
                readBuffer.getBytes(0, tmpBytes);
                byteBuf.writeBytes(tmpBytes);
                if (byteBuf.writerIndex() == byteBuf.capacity()) {
                    dealBytes(ctx, config, channel, byteBuf);
                }
            } else {
                byte[] preData = new byte[5];  //处理客户端发送的消息
                readBuffer.getBytes(0, preData);
                //提前获取所有字节内容
                int allDataLength = getDataLength(preData);
                //如果当前缓冲区的数据与标致位的长度一致则直接处理数据
                if (allDataLength + 4 == readableBytes) {
                    byte preDatum = preData[4];
                    switch (preDatum) {
                        case MySQLPacket.COM_QUERY:
                            dealBytes(ctx, config, channel, readBuffer);
                            break;
                        default:
                            readBuffer.retain();
                            channel.writeAndFlush(readBuffer);
                            break;
                    }

                } else {
                    //说明数据包不完全，先继续接收数据包等接收完全后再处理sql
                    ByteBuf tmpBuffer = Unpooled.buffer(allDataLength + 4);
                    tmpBuffer.writeBytes(readBuffer);
                    bufferMap.put(localPid, tmpBuffer);
                }
            }
        } else {
            readBuffer.retain();
            channel.writeAndFlush(readBuffer);
        }
    }

    /**
     * 处理完整数据包的字符内容
     * @param ctx
     * @param config
     * @param channel
     * @param byteBuf
     */
    private void dealBytes(ChannelHandlerContext ctx, ProxyConfig config, Channel channel, ByteBuf byteBuf) {
        int readableBytes = byteBuf.readableBytes();

        byte[] datas = new byte[readableBytes - 5];
        byteBuf.getBytes(5, datas);
        Charset charset = Charset.defaultCharset();
        String localPid = channel.localAddress().toString();

        String sql = new String(datas, Optional.ofNullable(ThreadPublic.getCharset(localPid)).orElse(defaultCharset));
        //替换掉客户端自己生成的注释语句
        sql = sql.replaceAll("(?ms)('(?:''|[^'])*')|--.*?$|/\\*.*?\\*/", "$1").trim();
        bufferMap.remove(localPid);
        if (sql.toUpperCase(Locale.ROOT).startsWith("SET NAMES")) {
            String charsetName = sql.replace("SET NAMES", "").trim();
            switch (charsetName) {
                case "utf8mb4":
                    charset = Charset.forName("utf8");
                    break;
                default:
                    break;
            }
            ThreadPublic.putCharset(localPid, charset);

        } else if (sql.toUpperCase(Locale.ROOT).startsWith("SELECT") || sql.toUpperCase(Locale.ROOT).contains("SELECT")) {
            sql = replaceSql(sql);
            byte[] newSqlBytes = sql.getBytes();
            int sqlLength = newSqlBytes.length + 1;
            byteBuf.writerIndex(0);
            byteBuf.writeByte((byte) (sqlLength & 0xff));
            byteBuf.writeByte((byte) (sqlLength >>> 8));
            byteBuf.writeByte((byte) (sqlLength >>> 16));
            byteBuf.writeByte((byte) 0);
            byteBuf.writeByte(MySQLPacket.COM_QUERY);
            byteBuf.writeBytes(newSqlBytes);
        } else if (sql.toUpperCase(Locale.ROOT).startsWith("DELETE")) {
            delete(ctx, config, channel, sql);
        } else if (sql.toUpperCase(Locale.ROOT).startsWith("UPDATE")) {
            update(ctx, config, channel, sql);
        } else if (sql.toUpperCase(Locale.ROOT).startsWith("INSERT")) {
            insert(ctx, config, channel, sql);
        }
        byteBuf.readerIndex(0);
        channel.writeAndFlush(byteBuf);


    }

    /**
     * 获取数据包长度
     * @param datas
     * @return
     */
    int getDataLength(byte[] datas) {
        return (datas[0] & 0xff) + ((datas[1] & 0xff) << 8) + ((datas[2] & 0xff) << 16);
    }

    /**
     * 替换查询语句的sql
     * @param sql
     * @return
     */
    public String replaceSql(String sql) {
        if (sql.toLowerCase(Locale.ROOT).startsWith("select") && sql.toLowerCase(Locale.ROOT).contains("from") && (!sql.toLowerCase(Locale.ROOT).contains("information_schema")) && (!sql.contains("*"))) {
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
            log.debug("替换后的的sql：{}", sql);
            return sql;
        }
        return sql;
    }
}
