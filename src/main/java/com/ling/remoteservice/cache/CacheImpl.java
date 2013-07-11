package com.ling.remoteservice.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.collections.map.LRUMap;

public class CacheImpl<K,V> implements ICache<K, V>{
    String cname;
    int csize;
    int captureSize;
    long exptime;
    Map<K,CacheEntry<V>> cache;


	private Lock writeLock;   
    long hitCount=0;
    long getCount=0;
    long writeCount=0;
    
    @SuppressWarnings("unchecked")
    public CacheImpl(String cachename,int cacheSize,long expireTime,boolean lru){
        this.cname=cachename;
        this.csize=cacheSize;
        this.exptime=expireTime;
        if (lru)
        	cache=new LRUMap(csize);
        else
        	cache=new HashMap<K, CacheEntry<V>>(csize);
        ReentrantReadWriteLock globalLock  = new ReentrantReadWriteLock();   
        //readLock = globalLock.readLock();   
        writeLock = globalLock.writeLock();   
    }

    public void put(K key,V val){
        try{
            writeLock.lock();
            writeCount++;
            cache.put(key,new CacheEntry<V>(exptime,val));
        }finally{
            writeLock.unlock();
        }
    }
    public V get(K key){
    	CacheEntry<V> ce= getCacheEntry(key);
    	if (ce==null) return null;
    	if (exptime<0){
    		return ce.getContent();
    	}
    	if (exptime>0)
    		if (!ce.isExpired()){
    			return ce.getContent();
    		}
    	return null;
       
    }
    public CacheEntry<V> getCacheEntry(K key){
        
    	if (exptime==0)
    		return null;
    	CacheEntry<V> c=null;
    	try{
    		writeLock.lock();
    		getCount++;
    		c=cache.get(key);
    	}finally{
    		writeLock.unlock();
        }        	       	
        if (c!=null){
        	hitCount++;
    		return c;
    		/*
    		 * 去掉此处的判定，过期的cacheEntry可能在某些场景可以被返回并使用
        	if (exptime<0){
        	    hitCount++;
        		return c;
        	}
        	if (exptime>0)
        		if (!c.isExpired()){
        		    hitCount++;
        			return c;
        		}
        	*/
        }
        return null;            
   
    }
    public void clear(K key){
        try{
            writeLock.lock();
            cache.remove(key);
        }finally{
            writeLock.unlock();
        }
    }
    public void clearAll(){
        try{
            writeLock.lock();
            cache.clear();
        }finally{
            writeLock.unlock();
        }
    }

    public String getCname() {
        return cname;
    }

    public int getCsize() {
        return csize;
    }

    public int getCaptureSize() {
        return cache.size();
    }

    public long getExptime() {
        return exptime;
    }

    public long getHitCount() {
        return hitCount;
    }

    public long getGetCount() {
        return getCount;
    }

    public long getWriteCount() {
        return writeCount;
    }
    public Map<K, CacheEntry<V>> getCache() {
		return cache;
	}
	public void setCache(Map<K, CacheEntry<V>> cache) {
		this.cache = cache;
	}
	@Override
	public void putCacheEntry(K key, CacheEntry<V> cacheent) {
		try{
            writeLock.lock();
            writeCount++;
            cache.put(key,cacheent);
        }finally{
            writeLock.unlock();
        }
	}

	@Override
	public CacheStatus getStatus(String cname) {
		CacheStatus cs = new CacheStatus();
		cs.setCacheSize(getCsize());// 分配大小 个数
		cs.setCacheName(getCname());
		cs.setCacheCapture(getCaptureSize());// 使用的
		cs.setExpireTime(getExptime());
		cs.setHitCount(getHitCount());
		cs.setGetCount(getGetCount());
		cs.setWriteCount(getWriteCount());
		return cs;
	}


}
