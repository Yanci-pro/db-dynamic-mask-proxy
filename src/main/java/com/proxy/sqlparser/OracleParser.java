package com.proxy.sqlparser;


import com.proxy.model.ProxyConfig;
import com.proxy.util.PrintUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * @description: 默认的处理方式，不做仍和处理
 * @author: yx
 * @date: 2021/12/8 10:20
 * https://blog.csdn.net/u013421629/article/details/84397586?utm_medium=distribute.pc_aggpage_search_result.none-task-blog-2~aggregatepage~first_rank_ecpm_v1~rank_v31_ecpm-1-84397586.pc_agg_new_rank&utm_term=linux%E5%AE%89%E8%A3%85%E9%85%8D%E7%BD%AEoracle%E5%AE%A2%E6%88%B7%E7%AB%AF&spm=1000.2123.3001.4430
 * <p>
 */
public class OracleParser extends DefaultSqlParser {
    String rule = "concat(SUBSTR(#field#,1,CHAR_LENGTH(#field#)/2),substr('*************',CHAR_LENGTH(#field#)/2,CHAR_LENGTH(#field#)/2)) as #field#";
    public static Charset defaultCharset = Charset.forName("gbk");

    public void dealChannel(ProxyConfig config, Channel channel, Object msg) {
        ByteBuf readBuffer = (ByteBuf) msg;
        int oldByteLength = readBuffer.readableBytes();


        InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
        String hostString = remoteAddress.getHostString();
        int remotePort = remoteAddress.getPort();
        if (Objects.equals(hostString, config.getRemoteAddr()) && Objects.equals(config.getRemotePort(), remotePort) && oldByteLength > 8) {
            byte[] allByte = new byte[oldByteLength];
            readBuffer.getBytes(0, allByte);
            String sql = new String(allByte);
            System.out.println("sql=" + sql);
            PrintUtil.print(allByte);
        }
        channel.writeAndFlush(readBuffer);

    }

}
