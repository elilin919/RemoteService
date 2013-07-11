package com.ling.remoteservice.cache;

import java.io.Serializable;

public class CacheStatus implements Serializable{
    /**
	 * 
	 */
	private static final long serialVersionUID = 5481375733486632791L;
	/**
	 * 
	 */
	
	String cacheName;
    int cacheSize;
    int cacheCapture;
    long expireTime;
    long hitCount,getCount,writeCount;
    public long getHitCount() {
        return hitCount;
    }
    public void setHitCount(long hitCount) {
        this.hitCount = hitCount;
    }
    public long getGetCount() {
        return getCount;
    }
    public void setGetCount(long getCount) {
        this.getCount = getCount;
    }
    public long getWriteCount() {
        return writeCount;
    }
    public void setWriteCount(long writeCount) {
        this.writeCount = writeCount;
    }
    public String getCacheName() {
        return cacheName;
    }
    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }
    public int getCacheSize() {
        return cacheSize;
    }
    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }
    public int getCacheCapture() {
        return cacheCapture;
    }
    public void setCacheCapture(int cacheCapture) {
        this.cacheCapture = cacheCapture;
    }
    public long getExpireTime() {
        return expireTime;
    }
    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }
    
}
