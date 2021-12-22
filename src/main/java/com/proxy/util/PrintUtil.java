package com.proxy.util;

/**
 * @description: 打印字节组的类，暂时使用这个便于调试，后续再考虑引入日志管理包
 * @author: yx
 * @date: 2021/12/15 19:54
 */
public class PrintUtil {


    public static void print(byte[] buffer, int start, int end) {
        System.out.print("-----start-----,");
        for (int i = start; i < end; i++) {
            System.out.print(buffer[i] + ",");

        }
        System.out.println(",-----end-----");
    }

    public static void print(byte[] buffer) {
        print(buffer, 0, buffer.length);
    }
}
