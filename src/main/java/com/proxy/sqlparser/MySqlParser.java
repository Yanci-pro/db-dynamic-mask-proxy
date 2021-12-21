package com.proxy.sqlparser;

import com.proxy.constants.MySQLPacket;
import com.proxy.model.ProxyConfig;
import com.proxy.util.PrintUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.apache.commons.lang.StringUtils;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.*;

import static com.proxy.constants.ThreadPublic.*;

/**
 * @description: some desc
 * @author: yx
 * https://github.com/MyCATApache/Mycat-Server.git
 * mysql的数据包格式:数据长度标识,前3位+分割为第四位固定为0,第五位表示操作动作(增删查改...)+最后一位据0结束符,
 * 据网上资料说明数据包最大长度为16M最后一位是春初下一个字节长度的,暂不验证,场景暂时用不到,不处理暂时不收影响
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
public class MySqlParser extends DefaultSqlParser {
    //脱敏算法，脱敏后面百分之五十
    String rule = "concat(SUBSTR(#field#,1,CHAR_LENGTH(#field#)/2),substr('*************',CHAR_LENGTH(#field#)/2,CHAR_LENGTH(#field#)/2)) as #field#";
    public static Charset defaultCharset = Charset.forName("gbk");

    @Override
    public void dealChannel(ProxyConfig config, Channel channel, Object msg) {

        ByteBuf readBuffer = (ByteBuf) msg;
        //如果是服务端发送的消息远程地址为空
        InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
        String hostString = remoteAddress.getHostString();
        int port = remoteAddress.getPort();
        //只有发送给数据库的数据才需要进行处理
        int readableBytes = readBuffer.readableBytes();
        if (hostString.equals(config.getRemoteAddr()) && Objects.equals(port, config.getRemotePort()) && readableBytes > 5) {
            //当前的数据流中的数据长度
            //前五位表示请求头
            byte[] preDatas = new byte[5];
            readBuffer.getBytes(0, preDatas);

            if (preDatas[4] == MySQLPacket.COM_QUERY) {
                String localPid = channel.localAddress().toString();
                Charset charset = getCharset(localPid);
                byte[] oldDatas = new byte[readableBytes - 5];
                readBuffer.getBytes(5, oldDatas);
                String sql = new String(oldDatas, Optional.ofNullable(charset).orElse(defaultCharset)).trim();
                //设置此次回话的数据格式
                if (sql.toUpperCase().startsWith("SET NAMES")) {
                    try {
                        putCharset(localPid, Charset.forName(sql.split(" ")[2]));
                    } catch (Exception e) {
                    }
                }
                //如果是查询语句咋替换sql
                if (sql.toUpperCase().trim().startsWith("SELECT")) {
                    dealQuerySql(readBuffer, preDatas, sql);
                }
            }
        }
        channel.writeAndFlush(readBuffer);
    }

    /**
     * 替换sql
     * <p>
     * 注意这里支持自己输入具体字段，不支持*号所有，数据来源需要自己解析
     *
     * @param readBuffer
     * @param preDatas
     * @param sql
     */
    public void dealQuerySql(ByteBuf readBuffer, byte[] preDatas, String sql) {
        //避开替换内置的schema表语句
        if (sql.toLowerCase().startsWith("select") && (!sql.toLowerCase().contains("information_schema"))) {
            int select = sql.toLowerCase().indexOf("select");
            int form = sql.indexOf("from");
            String substring = sql.substring(select, form).replace("select", "");
            String[] split = substring.split(",");
            List<String> list = new ArrayList<>();
            for (String column : split) {
                list.add(rule.replace("#field#", column));
            }
            String join = StringUtils.join(list, ",");
            String newSql = "select" + " " + join + " " + sql.substring(form);
            byte[] newSqlBytes = newSql.getBytes();

            System.out.println("newsql=" + newSql);
            PrintUtil.print(newSqlBytes);
            dealHeaderBytes(preDatas, newSqlBytes.length);
            PrintUtil.print(preDatas);

            readBuffer.writerIndex(0);
            readBuffer.writeBytes(preDatas);
            readBuffer.writeBytes(newSqlBytes);
        }
    }

    /**
     * 处理前三位标志位
     *
     * @param preDatas
     * @param length
     */
    void dealHeaderBytes(byte[] preDatas, int length) {
        //因为最后还有以一位是0结束位必须算进去
        length = length + 1;
        preDatas[0] = (byte) (length & 0xff);
        preDatas[1] = (byte) (length >>> 8);
        preDatas[2] = (byte) (length >>> 16);
    }
}
