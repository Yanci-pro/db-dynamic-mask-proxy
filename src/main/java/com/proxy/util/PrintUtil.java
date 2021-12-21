package com.proxy.util;

/**
 * @description: some desc
 * @author: yx
 * @date: 2021/12/15 19:54
 */
public class PrintUtil {
//  public static void print(byte[] bytes) {
//        System.out.print("-----start-----");
//        for (byte aByte : bytes) {
//            System.out.print(aByte + ",");
//        }
//        System.out.print("-----end-----");
//
//    }

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
