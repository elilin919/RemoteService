package com.ling.remoteservice.utils;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ContentHash {
	static final String key="taoban_url_hash";
	static MessageDigest messageDigest=null;
	static Log logger=LogFactory.getLog(ContentHash.class);
	static char[] chars = new char[] {
			'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't','u', 'v', 'w', 'x', 'y', 'z', 
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 
			'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T','U', 'V', 'W', 'X', 'Y', 'Z' };
	
	static { 
		try {
			messageDigest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			logger.error("",e);
		}
	}
	
	public static void main(String[] args) {
		
		String sLongUrl = "http://www.360buy.com/product/509634.html"; // 3B D7 68 E5-8042156E-54626860-E241E999  32/2 = 16 byte
		System.out.println(shortMD5("http://www.360buy.com/product/509634.html"));
		System.out.println(shortMD5("http://www.360buy.com/product/480534.html"));
	}

	public static String shortMD5(String url) {
		// 要使用生成 URL 的字符
		byte[] codes = md5(key + url, "utf8");
		StringBuilder res=new StringBuilder();
		for (int i=0;i<codes.length/2;i++){
			if (i>codes.length-2) break;
			
			byte b=codes[i*2];
			byte b2=codes[i*2+1];
			res.append(chars[ ((b+b2) & 0x003D) ]);
		}
		return res.toString();
	}

	private synchronized static byte[] md5(String content,String charset) {
        messageDigest.reset(); 
        try {
			messageDigest.update(content.getBytes(charset));
		} catch (UnsupportedEncodingException e) {
			logger.error("",e);
			
		} 
        return messageDigest.digest();
	}
}
