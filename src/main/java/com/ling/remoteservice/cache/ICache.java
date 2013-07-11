package com.ling.remoteservice.cache;

public interface ICache<K, V> {

	public CacheEntry<V> getCacheEntry(K key);
	
	public void putCacheEntry(K key,CacheEntry<V> cacheent);
	
	public void put(K key, V obj);

	public String getCname();

	public void clearAll();

	public void clear(K key);

	public CacheStatus getStatus(String cname);
	
}