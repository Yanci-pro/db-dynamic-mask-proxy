package com.proxy.sqlparser;

import java.nio.charset.Charset;

/**
 * @description: some desc
 * oracle的字符前十位是标志位，第六位是动作为，6表示查询,从第11位到第56位，共计16位暂不清楚用途
 * 实例
 * 0,33,0,0,6,0,0,0,0,0,
 * <p>
 * 0,33,0,0,6,0,0,0,0,0,---133
 * 0,19,0,0,6,0,0,0,0,0,---11
 * <p>
 * 收到：0,-113,0,0,6,0,0,0,0,0,  ---133
 * inputstring:0,19,0,0,6,0,0,0,0,0,
 * <p>
 * 收到：0,21,0,0,6,0,0,0,0,0,
 * inputstring:0,-113,0,0,6,0,0,0,0,0,  ---11
 * <p>
 * 现在的关键问题在于这个收到的字符串和inputstring发送的字符串不一样，与长度的关系。
 * T4C8Oall.marshalAll写入sql,426行
 * 这个位置是jdbc往服务段发送语句的地方，两边的内容一致
 * oracle.net.ns.DataPacket.send(int var1)
 * 发送的buffer创建位置
 * NSProtocol.
 * new AcceptPacket(this.packet);
 * <p>
 * 从输入流读取数据，下一步就是发送数据到服务段了
 * T4CMAREngine.unmarshalUB1（）
 * 重置游标的核心代码在
 * T4C8Oall.marshal()
 * @author: yx
 * @date: 2021/12/7 15:40
 * <p>
 * <p>
 * 关键代码标志位
 */
public class SqlServerParser extends DefaultSqlParser {
    String rule = "concat(SUBSTR(#field#,1,CHAR_LENGTH(#field#)/2),substr('*************',CHAR_LENGTH(#field#)/2,CHAR_LENGTH(#field#)/2)) as #field#";
    public static Charset defaultCharset = Charset.forName("gbk");


}
