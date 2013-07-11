package com.ling.remoteservice.cache;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import net.sf.cglib.core.Signature;

import com.ling.remoteservice.annonation.Cachable;
import com.ling.remoteservice.annonation.CacheUpdate;
import com.ling.remoteservice.annonation.Local;
import com.ling.remoteservice.annonation.Remote;

@Local(implementClass = "com.ling.remoteservice.cache.CacheManagerImpl")
@Remote(serviceName = "cache-manager")
public interface ICacheManager {

	public final static String CLEAR_ALL_CACHE = "CLEAR_ALL_CACHE";

	public void putIntoCache(Cachable cab, Signature sig, Method method, Object[] args, Object cacheResult);

	public CacheEntry<?> getFromCache(Cachable cab, Signature sig, Method method, Object[] args);

	public void updateCache(String serviceName, CacheUpdate cud, Signature sig,	Object[] args, boolean remoteModel);

	public void createNewCache(String cacheName, int size, long expireTime,	boolean diskcache); 
	
	public String getCachedValueAsString(String cname, String key);
	
	public void clearCache(String serviceName, String cname, String key, boolean remoteModel);

	Map<String, CacheStatus> getAllCacheStatus();

	List<String> getCacheNames();

	CacheStatus getCacheStatus(String cacheName);
}
