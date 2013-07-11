package com.ling.remoteservice;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.cache.CacheManagerImpl;
import com.ling.remoteservice.cache.ICacheManager;
import com.ling.remoteservice.msg.Messager;
import com.ling.remoteservice.msg.MessagerFactory;
import com.ling.remoteservice.msg.NetFlowListener;

public class LocalServiceManager {
	Log logger=LogFactory.getLog(LocalServiceManager.class);
	Messager messager=MessagerFactory.getInstance();
	
	Map<String,Boolean> localservices;
	static LocalServiceManager instance;
    public void createService(Class<?> clazz){
    	
    	if (localservices.containsKey(clazz.getName())) return;
    	
        logger.error("try load local service:"+clazz.getName());
        ServiceMessageListener listener=new ServiceMessageListener(clazz);
        messager.addListener(listener,Messager.LISTENER_TYPE_SERVER);
        logger.error("load service end :"+clazz.getName());
       
   }

	public static synchronized LocalServiceManager getInstance() {
		if (instance==null){
			instance=new LocalServiceManager();
		}
		return instance;
	}
    
	private LocalServiceManager(){
		localservices =new HashMap<String, Boolean>();
		localservices.put(ICacheManager.class.getName(), true);
		localservices.put(NetFlowListener.class.getName(), true);
		messager.addListener(new ServiceMessageListener(ICacheManager.class,CacheManagerImpl.getInstance()),Messager.LISTENER_TYPE_SERVER);
		
	}
    
}
