package com.ling.remoteservice.cache.memcached;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Configure {
	private  Map<String,String> config=new HashMap<String, String>();
	//spyMemcached,simpleMemcached,xmemcached...
	private  String clientType = "spyMemcached";
	private static Log logger=LogFactory.getLog(Configure.class);
	private static String SIGNATURE="";

	public Configure(){
		reloadConfig();
	}

	/**
	 *初始化配置文件
	 */
	public boolean reloadConfig() {
		boolean hasChange = false;
	    Properties props=new Properties();
		try{
			InputStream ins=Thread.currentThread().getContextClassLoader().getResourceAsStream("memcached.properties");
			if (ins!=null){
				props.load(ins);
				//get sig
				StringBuffer content=new StringBuffer();
				for (Entry<Object, Object> ent:props.entrySet()){
					content.append((String)ent.getKey()+","+(String)ent.getValue());
				}
				String sig = Md5Util.MD5Encode(content.toString());
				if(SIGNATURE.isEmpty() || !sig.equals(SIGNATURE)){
					hasChange = true;
					SIGNATURE = sig;
				}
				ins.close();
			}
			if(hasChange){
				for (Entry<Object, Object> ent:props.entrySet()){
					logger.info((String)ent.getKey()+","+(String)ent.getValue());
					if(((String)ent.getKey()).equals("clientType")){
						clientType = (String)ent.getValue();
					}else{
					    config.put((String)ent.getKey(),(String)ent.getValue());
					}
				}
			}
		}catch(Exception e){
			logger.error("",e);
		}
		return hasChange;
	}

	/**
	 *根据服务名得到memcachedServer列表
	 * @param cacheName
	 * @return servers(ip1:port ip2:port ip3:port...),or null
	 */
	public String getServersByCacheName(String cacheName){
		return config.get(cacheName);
	}

	/**
	 * 判断是否为memcached缓存
	 * @param cacheName
	 * @return true or false
	 */
	public boolean isMemcached(String cacheName){
		return config.containsKey(cacheName);
	}

	public String getClientType() {
		return clientType;
	}

	public Map<String, String> getConfig() {
		return config;
	}
}

class Md5Util {


    private final static String[] hexDigits = { "0", "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "a", "b", "c", "d", "e", "f" };

    public static String byteArrayToHexString(byte[] b) {
        StringBuffer resultSb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            resultSb.append(byteToHexString(b[i]));
        }
        return resultSb.toString();
    }

    private static String byteToHexString(byte b) {
        int n = b;
        if (n < 0)
            n = 256 + n;
        int d1 = n / 16;
        int d2 = n % 16;
        return hexDigits[d1] + hexDigits[d2];
    }

    /**
     * MD5Encode
     * @param origin
     * @return MD5 code
     */
    public static String MD5Encode(String origin) {
        String resultString = null;
        try {
            resultString = new String(origin);
            MessageDigest md = MessageDigest.getInstance("MD5");
            resultString = byteArrayToHexString(md.digest(resultString
                    .getBytes()));
        } catch (Exception ex) {

        }
        return resultString;
    }

    public static void main(String args[]) {
        System.out.println(Md5Util.MD5Encode("123456"));
    }
}
