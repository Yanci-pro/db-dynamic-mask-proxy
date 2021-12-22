package com.proxy.constants;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @description: some desc
 * @author: yx
 * @date: 2021/12/10 17:35
 */
public class ThreadPublic {
    public static Map<String, Charset> pidCharMap = new HashMap<>();

    public static void putCharset(String pid, Charset charSet) {
        pidCharMap.put(pid, charSet);
    }

    public static Charset getCharset(String pid ) {
        return pidCharMap.get(pid);
    }
}
