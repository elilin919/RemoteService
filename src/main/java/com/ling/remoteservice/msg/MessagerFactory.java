package com.ling.remoteservice.msg;

import java.net.SocketException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.ServiceMessageListener;
import com.ling.remoteservice.cache.CacheManagerImpl;
import com.ling.remoteservice.cache.ICacheManager;

public class MessagerFactory {
	
	static Log logger=LogFactory.getLog(MessagerFactory.class);
	static Messager messager;
	
    public synchronized static Messager getInstance(){
    	if (messager==null)
			try {
				messager=new MessagerImpl();

			} catch (SocketException e) {
				logger.error("", e);
			}
    	return messager;
    }
    
}
