package com.ling.remoteservice.utils;

import java.io.UnsupportedEncodingException;


public class BytesUtils {
    public static StringBuffer joinBytes(byte[] bytes){
        StringBuffer res=new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            res.append((int)bytes[i]).append("-");
        }
        return res;
    }
    // int、char、double与byte相互转换的程庄1�7
    // 整数到字节数组的转换
    public static byte[] longToByte(long number) {
        long temp = number;
        byte[] b = new byte[8];
        for (int i = b.length - 1; i > -1; i--) {
            b[i] = new Long(temp & 0xff).byteValue(); // 将最高位保存在最低位
            temp = temp >> 8; // 向右秄1�7�1�7
        }
        return b;
    }
    // 字节数组到整数的转换
    public static long byteToLong(byte[] b) {
        long s = 0;
        for (int i = 0; i < 7; i++) {
            if (b[i] >= 0)
                s = s + b[i];
            else

                s = s + 256 + b[i];
            s = s * 256;
        }
        if (b[7] >= 0) // 朄1�7后一个之扄1�7以不乘，是因为可能会溢出
            s = s + b[7];
        else
            s = s + 256 + b[7];
        return s;
    }    
    // int、char、double与byte相互转换的程庄1�7
    // 整数到字节数组的转换
    public static byte[] intToByte(int number) {
        int temp = number;
        byte[] b = new byte[4];
        for (int i = b.length - 1; i > -1; i--) {
            b[i] = new Integer(temp & 0xff).byteValue(); // 将最高位保存在最低位
            temp = temp >> 8; // 向右秄1�7�1�7
        }
        return b;
    }
    // 字节数组到整数的转换
    public static int byteToInt(byte[] b) {
        int s = 0;
        for (int i = 0; i < 3; i++) {
            if (b[i] >= 0)
                s = s + b[i];
            else

                s = s + 256 + b[i];
            s = s * 256;
        }
        if (b[3] >= 0) // 朄1�7后一个之扄1�7以不乘，是因为可能会溢出
            s = s + b[3];
        else
            s = s + 256 + b[3];
        return s;
    }
    
    public static String bytesToString(byte[] strbytes){
        try {
            return new String(strbytes,"UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static byte[] stringToBytes(String str){
        try {
            return str.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }
}
