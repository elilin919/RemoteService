package com.ling.remoteservice.cache.memcached;

import java.util.Collection;
import java.util.Map;

import com.ling.remoteservice.cache.CacheEntry;
import com.ling.remoteservice.cache.CacheStatus;
import com.ling.remoteservice.cache.ICache;

public interface IMemcachedClient<V> extends ICache<String, V>{
	public void put(String key,  CacheEntry<V> entry);
	public void put(String key,  V obj);
	public  CacheEntry<V> getCacheEntry(String key);
	public CacheEntry<V>  getCacheEntry(String key,int retryTimes);
	public void clear();
	public boolean exists(String key) ;
	public void close() ;
	public CacheStatus  getStatus(String cachename);
	public Map<String, Object> asyncGetBulk(Collection<String> keys);
}
