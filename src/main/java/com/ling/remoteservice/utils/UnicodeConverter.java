package com.ling.remoteservice.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;

public class UnicodeConverter {

	static public String charToHex(char c) {
		// Returns hex String representation of char c
		byte hi = (byte) (c >>> 8);
		byte lo = (byte) (c & 0xff);
		return byteToHex(hi) + byteToHex(lo);
	}

	static public String byteToHex(byte b) {
		// Returns hex String representation of byte b
		char hexDigit[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
				'a', 'b', 'c', 'd', 'e', 'f' };
		char[] array = { hexDigit[(b >> 4) & 0x0f], hexDigit[b & 0x0f] };
		return new String(array);
	}

	static public String doConverter(String gbString) {
		StringBuffer uniString = new StringBuffer();
		try {
			char[] charArray = (new String(gbString.getBytes(), "GBK"))
					.toCharArray();
			for (int i = 0; i < charArray.length; i++) {
				char charA = charArray[i];
				uniString.append("&#x").append(charToHex(charA)).append(";");
				//System.out.print("&#x"+charToHex(charA)+";");
			}
			return uniString.toString();
		} catch (Exception e) {
			System.err.println("error: " + e.toString());
			return uniString.toString();
		}
	}
	static public String doConverterMixModel(String gbisoString) {
		return doConverterMixModel(gbisoString,"iso8859-1");
	}
	static public String doConverterMixModel(String gbencstr,String encode) {
		StringBuffer uniString = new StringBuffer();
		try {
			char[] charArray = null;
			if (!encode.equals("GBK"))
				charArray=(new String(gbencstr.getBytes(encode), "GBK")).toCharArray();
			else
				charArray=gbencstr.toCharArray();
			
			for (int i = 0; i < charArray.length; i++) {
				char charA = charArray[i];
				if (charA > 0xff)
					uniString.append("&#x").append(Integer.toHexString(charA))
							.append(";");
				else
					uniString.append(charA);
				//System.out.print("&#x"+charToHex(charA)+";");
			}
			return uniString.toString();
		} catch (Exception e) {
			System.err.println("error: " + e.toString());
			return uniString.toString();
		}
	}

	static public String recoverUnicode(String unicode) {

		java.util.regex.Pattern patten = java.util.regex.Pattern
				.compile("&#x[0-9a-fA-F]{2,4};");
		Matcher matcher = patten.matcher(unicode);
		StringBuffer res = new StringBuffer();
		int start = 0;
		int end = 0;
		while (matcher.find()) {
			int substart = matcher.start();
			int subend = matcher.end();
			String chars = matcher.group();
			chars = chars.substring(3, chars.length() - 1);
			end = substart;
			res.append(unicode.substring(start, end));
			res.append((char) Integer.parseInt(chars, 16));
			start = subend;
		}
		res.append(unicode.substring(start));
		return res.toString();

	}

	/** Make a java-style UTF-8 encoded byte array for a string. */
	public static byte[] toUTF8(String str) {
		// first count how many bytes we'll need.
		int len = 0;
		for (int i = 0; i < str.length(); i++) {
			int c = str.charAt(i);
			if (c >= 0x0001 && c <= 0x007f)
				len += 1; // one byte format
			else if (c == 0 || (c >= 0x0080 && c <= 0x07FF))
				len += 2; //two byte
			else
				len += 3; // three byte format.
		}
		// allocate byte array for result.
		byte[] r = new byte[len];
		// now make the UTF-8 encoding.
		len = 0;
		for (int i = 0; i < str.length(); i++) {
			int c = str.charAt(i);
			if (c >= 0x0001 && c <= 0x007f) { // one byte format
				r[len++] = (byte) c;
			} else if (c == 0 || (c >= 0x0080 && c <= 0x07FF)) { // two byte
				r[len++] = (byte) (0xC0 | (c >>> 6));
				r[len++] = (byte) (0x80 | (c & 0x3F));
			} else { // three byte format
				r[len++] = (byte) (0xE0 | (c >>> 12));
				r[len++] = (byte) (0x80 | ((c >>> 6) & 0x3F));
				r[len++] = (byte) (0x80 | (c & 0x3F));
			}
		}
		// okay, done.
		return r;
	}
	
	public static String UTF8(String str) {
		if (str==null) return null;
		if (str.length()==0) return "";
		// first count how many bytes we'll need.
		//StringWriter cwriter=new StringWriter(str.length()*3);
		ByteArrayOutputStream bins=new ByteArrayOutputStream(str.length()*3);
		// allocate byte array for result.
		// now make the UTF-8 encoding.
		//int len = 0;
		for (int i = 0; i < str.length(); i++) {
			int c = str.charAt(i);
			if (c >= 0x0001 && c <= 0x007f) { // one byte format
				bins.write((byte)c);
			} else if (c == 0 || (c >= 0x0080 && c <= 0x07FF)) { // two byte
				bins.write((byte) (0xC0 | (c >>> 6)));
				bins.write((byte) (0x80 | (c & 0x3F)));
			} else { // three byte format
				bins.write((byte) (0xE0 | (c >>> 12)));
				bins.write((byte) (0x80 | ((c >>> 6) & 0x3F)));
				bins.write((byte) (0x80 | (c & 0x3F)));
			}
		}
		// okay, done.
		try {
			return bins.toString("UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}
	public static String BytesToHexStr(byte[] bb) {
		char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
				'A', 'B', 'C', 'D', 'E', 'F' };
		int i = 0;
		String sRtn = "";
		for (i = 0; i < bb.length; i++) {
			sRtn = sRtn + hexDigits[(bb[i] >> 4) & 0x0f];
			sRtn = sRtn + hexDigits[bb[i] & 0x0f];
		}
		return sRtn;
	}
	
    public static String UTF2GB(String utf8Str) throws IOException{
        if (utf8Str==null) return null;
        if (utf8Str.length()==0) return "";
        byte[] bytearr=utf8Str.getBytes("UTF-8");
        int utflen = bytearr.length;
        StringBuffer str = new StringBuffer(utflen);
        int count = 0;
        while(count < utflen)
        {
            int c = bytearr[count] & 0xff;
            switch(c >> 4)
            {
            case 0: // '\0'
            case 1: // '\001'
            case 2: // '\002'
            case 3: // '\003'
            case 4: // '\004'
            case 5: // '\005'
            case 6: // '\006'
            case 7: // '\007'
            {
                count++;
                str.append((char)c);
                break;
            }

            case 12: // '\f'
            case 13: // '\r'
            {
                if((count += 2) > utflen)
                    throw new UTFDataFormatException();
                int char2 = bytearr[count - 1];
                if((char2 & 0xc0) != 128)
                    throw new UTFDataFormatException();
                str.append((char)((c & 0x1f) << 6 | char2 & 0x3f));
                break;
            }

            case 14: // '\016'
            {
                if((count += 3) > utflen)
                    throw new UTFDataFormatException();
                int char2 = bytearr[count - 2];
                int char3 = bytearr[count - 1];
                if((char2 & 0xc0) != 128 || (char3 & 0xc0) != 128)
                    throw new UTFDataFormatException();
                str.append((char)((c & 0xf) << 12 | (char2 & 0x3f) << 6 | (char3 & 0x3f) << 0));
                break;
            }

            case 8: // '\b'
            case 9: // '\t'
            case 10: // '\n'
            case 11: // '\013'
            default:
            {
                throw new UTFDataFormatException();
            }
            }
        }
        return str.toString();
    }
}
