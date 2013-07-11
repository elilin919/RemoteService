package com.ling.remoteservice.cache;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.cglib.core.Signature;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.Configure;
import com.ling.remoteservice.annonation.Cachable;
import com.ling.remoteservice.annonation.CacheUpdate;
import com.ling.remoteservice.cache.memcached.MemcachedManager;
import com.ling.remoteservice.msg.Messager;
import com.ling.remoteservice.msg.MessagerFactory;
import com.ling.remoteservice.utils.TypeUtils;

public class CacheManagerImpl implements ICacheManager {
	
	static Log logger=LogFactory.getLog(CacheManagerImpl.class);
	
	static Map<String, ICache<String, Object>> cachesmap;
	MemcachedManager memcachedManager;
	Lock rlock, wlock;
	Configure cacheConfig;
	CacheSyncListener cachesycer;
	
	Pattern paramPattern = Pattern.compile("(?i)\\$params\\[(\\d*)\\](.*)");
	
	
	static ICacheManager instance;
	public static synchronized ICacheManager getInstance() {
		if (instance==null){
			instance=new CacheManagerImpl();
		}
		return instance;
	}
	private CacheManagerImpl(){
		cacheConfig = new Configure("cacheConfig.properties");
		cachesmap= new HashMap<String, ICache<String, Object>>(); 
		memcachedManager= MemcachedManager.getInstance();
		ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock();
		rlock = rwlock.readLock();
		wlock = rwlock.writeLock();
		
		cachesycer = new CacheSyncListener(this, cacheConfig.getPropertiesByPrefix("CACHE-SYNC-SERVER") );
		MessagerFactory.getInstance().addListener(cachesycer, Messager.LISTENER_TYPE_SERVER); //client become a server, to received clear message from remote server.	
	}

	@Override
	public void putIntoCache(Cachable cab, Signature sig, Method method, Object[] args, Object ret) {
		Class<?> rType=method.getReturnType();
		if (ret!=null){
            if ( ret.getClass()==rType)
                put(cab, sig, method, args, ret);
            else
                if (TypeUtils.typeMatch(ret.getClass(), rType))
                    put(cab, sig, method, args, ret);
                else
                    logger.error("return result type:["+ret.getClass()
                            +"]vs return type:["+rType
                            +"] isn't match! execmethod :"
                            +sig.toString());
        }
		else{
            if (cab.cacheNull()){
            	//配置为可缓存空值
            	put(cab, sig, method, args, null);
            }
        }
	}

	@Override
	public void updateCache(String serviceName, CacheUpdate cu, Signature sig, Object[] args, boolean remoteModel) {
		if (cu != null) {
			for (int i = 0; i < cu.cacheName().length; i++) {
				String cname = parseKey(cu.cacheName()[i], args);
				String key = parseKey(cu.updateKey()[i], args);
				
				if (!memcachedManager.clear(cname,key)) {
					clearCache(serviceName, cname, key, remoteModel);
				}
			}

		} else {
			logger.debug("cacheUpdate annonation for:" + sig + " is null.");
		}
	}

	public void clearCache(String serviceName, String cname, String key, boolean remoteModel) {
		ICache<String, Object> c = getCache(cname);
		if (c != null) {
			if (CLEAR_ALL_CACHE.equals(key)) {
				logger.debug("clear all cache of :cache[" + c.getCname() + "]");
				c.clearAll();
			} else {
				logger.debug("clear cache of :cche[" + c.getCname() + "]:" + key);
				c.clear(key);
			}
			// 从server端，清除远端缓存
			if (!remoteModel && serviceName != null) {
				logger.debug("not a remote model. try send clear all  cmd for:cache[" + c.getCname() + "]");
				cachesycer.sendClear(serviceName, c.getCname(), key, null, 0);
			}
		}
	}

	@Override
	public CacheEntry<?> getFromCache(Cachable cab, Signature sig, Method method, Object[] args) {
		Class<?> rType=method.getReturnType();
		CacheEntry<?> cachedEntry = get(cab, sig, method, args);
		if (cachedEntry!=null){
			Object cachedRet=cachedEntry.getContent();
			if (cachedRet!=null && cachedRet.getClass()!=rType){
	    		//预期缓存返回值和实际缓存值不符。
	            if (!TypeUtils.typeMatch(cachedRet.getClass(), rType)){
	            	cachedEntry=null;
	            }
			}
		}
		return cachedEntry;
	}

	public String getCachedValueAsString(String cname, String key) {
		Object destObj = null;
		CacheEntry<?> ce= get(cname, key);
		if (ce!=null) destObj = ce.getContent();
		if (destObj == null)
			return "CACHE VALUE IS NULL FOR KEY :" + key;
		StringBuilder buff=new StringBuilder(destObj.toString());
		buff.append("\n");
		Field[] fields=destObj.getClass().getDeclaredFields();
		for (Field f:fields){
			try {
				f.setAccessible(true);
				buff.append(f.getName()).append(":").append(f.get(destObj)).append("\n");
			} catch (Exception e) {
				logger.error("",e);
			}
		}
		return buff.toString();
	}
	
	private CacheEntry<?> get(Cachable ca, Signature sig, Method method, Object[] args) {
		if (ca != null) {
			for (int i = 0; i < ca.cacheName().length; i++) {
				// -add by tj,memcached cache
				String cname = parseKey(ca.cacheName()[i], args);
				String key = parseKey(ca.cacheKey()[i], args);
				return get(cname, key);
			}
		} else {
			logger.debug("Cachable annonation for:" + sig + " is null.");
		}
		return null;
	}

	private CacheEntry<?> get(String cname, String key) {
		if (memcachedManager.userMemcached(cname)) {
			return memcachedManager.get(cname,key);
		}
		ICache<String, Object> c = getCache(cname);
		if (c != null) {
			CacheEntry<?> ce = c.getCacheEntry(key);
			return ce;
		}else{
			logger.debug("Cache ["+cname+"] is null.");
			return null;
		}
	}

	private void put(Cachable cn, Signature sig, Method method, Object[] args, Object val) {
		if (cn != null) {
			for (int i = 0; i < cn.cacheName().length; i++) {
				
				String cname = parseKey(cn.cacheName()[i], args);
				String key = parseKey(cn.cacheKey()[i], args);
				
				if (!memcachedManager.put(cname,key,val)) {
					ICache<String, Object> c = getCache(cn,i,cname,true);
					if (c != null) {
						logger.debug("add [" + key + "] to cache[" + cname + "]");
						c.put(key, val);
					}else{
						logger.debug("Cache ["+cname+"] is null.");
					}
				}
			}
		}else{
			logger.info("There's no Cache Annonation registed for :"+sig);
		}
	}
	
	private ICache<String, Object> getCache(Cachable cn, int idx, String cname, boolean create) {
		try{
			rlock.lock();
			ICache<String,Object> cache=cachesmap.get(cname);
			if (cache!=null)
				return cache;
		}finally{
			rlock.unlock();
		}
		ICache<String,Object> cache=createCache(cname, cn.cacheSize()[idx], cn.expireTime()[idx], cn.diskCache(), false);
		return cache;
	}
	private ICache<String, Object> getCache(String cname) {
		try{
			rlock.lock();
			ICache<String,Object> cache=cachesmap.get(cname);
			if (cache!=null)
				return cache;
		}finally{
			rlock.unlock();
		}
		return null;
	}
	
	public  void createNewCache(String cacheName, int size, long expireTime, boolean diskcache){
		createCache(cacheName, size, expireTime, diskcache, true);
	}
	
	private ICache<String, Object> createCache(String cacheName, int size, long expireTime, boolean diskcache, boolean saveconfig) {
		
		if (memcachedManager.userMemcached(cacheName)) {
			return null;
		}
		ICache<String, Object> c=null;
		try {
			if (!saveconfig){
				Map<String,String> savedConfig = cacheConfig.getMapByPrefix(cacheName+".");
				if (savedConfig != null && savedConfig.size()>0) {
					size = Integer.parseInt(savedConfig.get("size"));
					expireTime = Long.parseLong(savedConfig.get("expireTime"));
					diskcache = "true".equals(savedConfig.get("diskCache"));
				}
			}else{
				cacheConfig.saveConfig(
						new String[]{cacheName+".size", ""+size},
						new String[]{cacheName+".expireTime", ""+expireTime},
						new String[]{cacheName+".diskCache", ""+diskcache}
				);
			}
			c = new CacheImpl<String, Object>(cacheName, size, expireTime, true);
		} catch (Exception e) {
			logger.error("", e);
		}
		try{
			wlock.lock();
			cachesmap.put(cacheName, c);
		}finally{
			wlock.unlock();
		}
		return c;
	}
	
	private String parseKey(String cacheKeyStr, Object[] args) {
		String[] cacheKey = cacheKeyStr.split(",");
		StringBuffer res = new StringBuffer();
		for (String ck : cacheKey) {
			if (res.length() > 0)
				res.append(",");
			if (CLEAR_ALL_CACHE.equals(ck))
				return CLEAR_ALL_CACHE;
			Matcher m = paramPattern.matcher(ck);
			if (m.find()) {
				int idx = Integer.parseInt(m.group(1));
				String prop = m.group(2);
				// logger.info("cachekey:"+cacheKeyStr+":"+prop+":for
				// :"+args[idx]);
				String value = TypeUtils.getProperties(args[idx], prop);
				res.append(value);
			} else {
				res.append(ck);
			}
			// res.append(",");
		}
		return res.toString();
	}

	
	@Override
	public Map<String, CacheStatus> getAllCacheStatus() {
		Map<String, CacheStatus> res = new HashMap<>();
		Map<String, ICache<String, Object>> clist = new HashMap<>();
		clist.putAll(cachesmap);
		for (ICache<String, Object> c : clist.values()) {
			res.put(c.getCname(), getCacheStatus(c));
		}
		// -add by tj,memcached cache
		res.putAll(memcachedManager.getAllCacheStatus());
		return res;
	}

	private CacheStatus getCacheStatus(ICache<String, Object> c) {
		// -add by tj,memcached cache
		String cacheName = c.getCname();
		if (memcachedManager.userMemcached(cacheName)) {
			return memcachedManager.getMemcachedClient(cacheName).getStatus(cacheName);
		}
		CacheStatus cs = c.getStatus(cacheName);//new CacheStatus();
		return cs;
	}

	@Override
	public List<String> getCacheNames() {
		List<String> l = new ArrayList<String>();
		l.addAll(cachesmap.keySet());
		// -add by tj,memcached cache
		l.addAll(memcachedManager.getCacheNames());
		return l;
	}

	@Override
	public CacheStatus getCacheStatus(String cacheName) {
		// -add by tj,memcached cache
		if (memcachedManager.userMemcached(cacheName)) {
			return memcachedManager.getMemcachedClient(cacheName).getStatus(cacheName);
		}
		rlock.lock();
		ICache<String, Object> c = cachesmap.get(cacheName);
		rlock.unlock();
		return getCacheStatus(c);
	}


}
