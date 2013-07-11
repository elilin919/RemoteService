package com.ling.remoteservice;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.sf.cglib.proxy.Enhancer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.proxy.LocalCacheInterceptor;

public class Manager {

	Log logger;
	
	//Configure config;
	static Manager manager;	
	//boolean clientModel=false;
	Map<String,Object> instances;
	
	Map<String,Lock> instanceLockers;
	
	public static Manager getInstance(){
		if (manager==null)
			synchronized (Manager.class){
				if (manager==null)
					manager=new Manager();
			}
		return manager;
	}
	private Manager(){
	   
		instances=new ConcurrentHashMap<>();
		instanceLockers=new HashMap<String, Lock>();
		logger=LogFactory.getLog(getClass());
		//initServers();
		
		//cache service for every instance.
		//Manager.getInstance().createService(ICacheManager.class);
	}
	
    @SuppressWarnings("unchecked")
    public  <T> T createInstance(Class<T> clazz,boolean useCache,Object... initparams) {
    	Lock locker=getLock(clazz);
    	try{
    		locker.lock();
	        T proxyObject=(T)instances.get(clazz.getName()+"("+getParamType(initparams)+")cache("+useCache+")");
	        if (proxyObject==null)
	        try{
	            logger.info("create new Operations:"+clazz.getName());
	            Enhancer enc=new Enhancer();
	            enc.setSuperclass(clazz);
	            enc.setCallback(new LocalCacheInterceptor(clazz,useCache,initparams));
	            proxyObject=(T) enc.create();
	            instances.put(clazz.getName()+"("+getParamType(initparams)+")cache("+useCache+")", proxyObject);
	        }catch(Exception e){
	            logger.error("",e);
	        }
	        return proxyObject;
    	}finally{
    		locker.unlock();
    	}
    }
    private String getParamType(Object[] initparams) {
        StringBuffer ref=new StringBuffer();
        for (Object p:initparams){
            ref.append(p.getClass().getName()).append(",");
        }
        return ref.toString();
    }
    
    @SuppressWarnings("unchecked")
    public  <T> T createInstance(Class<T> clazz) {
    	Lock locker=getLock(clazz);
    	try{
    		locker.lock();
			T proxyObject=(T)instances.get(clazz.getName());
			if (proxyObject==null)
			try{
			    logger.error("create new Operations:"+clazz.getName());
			    Enhancer enc=new Enhancer();
			    enc.setSuperclass(clazz);
			    enc.setCallback(new LocalCacheInterceptor(clazz));
			    proxyObject=(T) enc.create();
			    if (proxyObject==null) 
			    	logger.error("bad proxy instance for "+clazz);
				instances.put(clazz.getName(), proxyObject);
			}catch(Exception e){
				logger.error("",e);
			}
			return proxyObject;
    	}finally{
    		locker.unlock();
    	}
	}
    @SuppressWarnings("unchecked")
    public  <T> T createInstance(Class<T> clazz,Object implementObj) {
    	if (implementObj==null) return createInstance(clazz);
    	Lock locker=getLock(clazz);
    	try{
    		locker.lock();
			T proxyObject=(T)instances.get(clazz.getName());
			if (proxyObject==null)
			try{
			    logger.error("create new Operations:"+clazz.getName());
			    Enhancer enc=new Enhancer();
			    enc.setSuperclass(clazz);
			    enc.setCallback(new LocalCacheInterceptor(clazz,implementObj));
			    proxyObject=(T) enc.create();
			    if (proxyObject==null) 
			    	logger.error("bad proxy instance for "+clazz);
				instances.put(clazz.getName(), proxyObject);
			}catch(Exception e){
				logger.error("",e);
			}
			return proxyObject;
    	}finally{
    		locker.unlock();
    	}
	}
    /**
     * 创建代理实例
     * @param <T>
     * @param clazz  接口
     * @param serviceName  服务名
     * @return
     */
    @SuppressWarnings("unchecked")
    public  <T> T createInstance(Class<T> clazz,boolean forceRemote) {
    	if (!forceRemote) return createInstance(clazz);
    	
    	Lock locker=getLock(clazz);
    	try{
    		locker.lock();
	        T implObj=(T)instances.get(clazz.getName()+"@force-remote");
	        if (implObj==null)
	        try{
	            Enhancer enc=new Enhancer();
	            enc.setSuperclass(clazz);
	            enc.setCallback(new LocalCacheInterceptor(clazz,forceRemote));
	            implObj=(T) enc.create();
	            instances.put(clazz.getName()+"@force-remote", implObj);
	        }catch(Exception e){
	            logger.error("",e);
	        }
	        return implObj;
    	}finally{
    		locker.unlock();
    	}
    }
    /**
     * 创建代理实例
     * @param <T>
     * @param clazz  接口
     * @param serviceName  服务名
     * @return
     */
    @SuppressWarnings("unchecked")
    public  <T> T createInstance(Class<T> clazz,String serviceName,long timeout,int retry) {
    	Lock locker=getLock(clazz);
    	try{
    		locker.lock();
	        T implObj=(T)instances.get(clazz.getName()+"@"+serviceName);
	        if (implObj==null)
	        try{
	            Enhancer enc=new Enhancer();
	            enc.setSuperclass(clazz);
	            enc.setCallback(new LocalCacheInterceptor(clazz,serviceName,timeout,retry));
	            implObj=(T) enc.create();
	            instances.put(clazz.getName()+"@"+serviceName, implObj);
	        }catch(Exception e){
	            logger.error("",e);
	        }
	        return implObj;
    	}finally{
    		locker.unlock();
    	}
    }
    private Lock getLock(Class<?> clazz){
    	synchronized (instanceLockers) {
    		Lock lock=instanceLockers.get(clazz.getName());
    		if (lock!=null) return lock;
    		lock=new ReentrantLock();
    		instanceLockers.put(clazz.getName(), lock);
    		return lock;
		}
    }
}
