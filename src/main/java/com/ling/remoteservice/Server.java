package com.ling.remoteservice;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.annonation.Local;
import com.ling.remoteservice.msg.Messager;
import com.ling.remoteservice.msg.MessagerFactory;

public class Server extends Thread {
    static Log logger = LogFactory.getLog(Server.class);
    boolean running=true;
    Messager messager;
    Map<String,ServiceMessageListener> serverList=null;
    ServiceClassLoader loader;
    
    public Server(){
    	loader  = new ServiceClassLoader();
    }
    
    public void run(){
    	serverList=new HashMap<>();
        messager=MessagerFactory.getInstance();
        LocalServiceManager services=LocalServiceManager.getInstance();
        Map<Local,Class<?>>  servicelist=loader.getServiceClasses();
        for (Entry<Local, Class<?>> ent: servicelist.entrySet()){
        	Class<?> clazz=ent.getValue();
            //Local loc=ent.getKey();
        	services.createService(clazz);
        }
    }
    
    public void close(){
        running=false;
        try{
        	messager.close();
        }catch(Exception e){
        	logger.error("",e);
        }
    }

}


