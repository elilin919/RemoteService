package com.ling.remoteservice.cache.memcached;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.cache.CacheEntry;
import com.ling.remoteservice.cache.CacheStatus;

public class MemcachedManager extends ReentrantLock implements Serializable{

	private static final long serialVersionUID = -2182510240092852911L;

	private Log logger = LogFactory.getLog(getClass());

	private Map<String, IMemcachedClient<Object>> map = new ConcurrentHashMap<>();

	static final int CHECK_CYCLE=600;//配置文件检查周期600秒

	private Configure configure = null;

	//sigleton
	private static MemcachedManager manager = null;
	private MemcachedManager() {
		configure = new Configure();
		initMemcached();
		checkConfigDeamon();
	}
	public static MemcachedManager getInstance() {
		if (manager == null) {
			manager = new MemcachedManager();
		}
		return manager;
	}

	private void checkConfigDeamon(){
		 ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
			Runnable beeper = new Runnable() {
				public void run() {
					//定时检查memcached.properties，自动reload。
					logger.debug("check memcached.properties.");
					try{
						if(configure.reloadConfig()){
							logger.info("reload memcached.properties.");
							initMemcached();
						}
					}catch(Exception e){
						e.printStackTrace();
					}
				}
			};
		//0秒钟后运行，并每次在上次任务运行完后等待1小时后重新运行
		scheduler.scheduleWithFixedDelay(beeper, CHECK_CYCLE, CHECK_CYCLE, SECONDS);
	}

	private void initMemcached(){
		Map<String, IMemcachedClient<Object>> service_map = new HashMap<>();
		Map<String, String> cahcheName_map = new HashMap<String, String>();
		Map<String, String> config = configure.getConfig();
		//第一次循环，初始化memcached client
		for (Entry<String, String> entry : config.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			//以‘service_’开头的为memcached服务配置。
			//cacheName直接对应‘service_’。
			if(key.startsWith("service_")){
				logger.info("init MemcachedManager();"+key+":"+value+"\t"+check(value));
				if (check(value)) {
					IMemcachedClient<Object> client = new SpyMemcachedClient<>(key,value);
					service_map.put(key, client);
				} else {
					logger.error("init MemcachedManager() faild,the memcached.properties has error!");
					throw new IllegalArgumentException(
					"init MemcachedManager() faild,the memcached.properties has error!");
				}
			}else{
				cahcheName_map.put(key, value);
			}
		}
		//第二次循环，设置cacheName对应的memcached client
    	Map<String, IMemcachedClient<Object>> _map = new ConcurrentHashMap<>();
 		for (Entry<String, String> entry : cahcheName_map.entrySet()) {
 			String key = entry.getKey();
 			String value = entry.getValue();
 			if(service_map.containsKey(value)){
 				logger.info("cacheName:"+key+"\t"+value);
 				_map.put(key, service_map.get(value));
 			}else{
 				logger.error("cacheName:["+key+ "]can't init , can't find service:"+value);
 			}
 		}
 		map = _map;
	}

	public IMemcachedClient<?> getMemcachedClient(String cacheName) {
		return map.get(cacheName);
	}

	public boolean userMemcached(String cacheName){
		return map.containsKey(cacheName);
	}

	/**
	 * check service ip
	 * @param ipStr
	 * @return
	 */
	private boolean check(String ipStr) {
		String[] split = ipStr.split(" ");
		if(split.length==0){
			return false;
		}
		for(String str:split){
			String[] split2 = str.split(":");
			if(split2.length!=2){
				return false;
			}
			String[] split3 = split2[0].split("\\.");
			if(split3.length!=4){
				return false;
			}
		}
		return true;
	}


    public List<String> getCacheNames(){
    	List<String> l = new ArrayList<String>();
		l.addAll(map.keySet());
    	return l;
    }

    public CacheStatus  getCacheStatus(String cacheName){
    	CacheStatus status = null;
    	if(map.containsKey(cacheName)){
    		status = map.get(cacheName).getStatus(cacheName);
    	}
    	return status;
    }

    public Map<String,CacheStatus>  getAllCacheStatus(){
    	Map<String,CacheStatus> reMap = new HashMap<String,CacheStatus>();
    	for(Entry<String,IMemcachedClient<Object>> entry:map.entrySet()){
    		String key = entry.getKey();
    		IMemcachedClient<Object> value = entry.getValue();
    		reMap.put(key, value.getStatus(key));
    	}
    	return reMap;
    }
    
	public boolean clear(String cacheName, String key) {
		if(map.containsKey(cacheName)){
    		map.get(cacheName).clear(key);
    		return true;
    	}
		return false;
	}

	public boolean put(String cacheName, String key, Object val) {
		if(map.containsKey(cacheName)){
    		map.get(cacheName).put(key,val);
    		return true;
    	}
		return false;
	}
	public CacheEntry<?> get(String cacheName, String key) {
		if(map.containsKey(cacheName)){
    		return map.get(cacheName).getCacheEntry(key);
    	}
		return null;
	}
}
