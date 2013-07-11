package com.ling.remoteservice.cache.memcached;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.spy.memcached.AddrUtil;
import net.spy.memcached.ConnectionFactory;
import net.spy.memcached.DefaultConnectionFactory;
import net.spy.memcached.KetamaConnectionFactory;
import net.spy.memcached.MemcachedClient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.cache.CacheEntry;
import com.ling.remoteservice.cache.CacheStatus;

public class SpyMemcachedClient<V> implements IMemcachedClient<V> {
	private Log logger = LogFactory.getLog(getClass());

	private MemcachedClient client;

	private String cacheName;

	// if client number > this number,use KetamaConnectionFactory
	private static final int CLIENT_NUMBER = 3;
	
	long cacheExpiredTime = 60000l*60*24*30; //30 days cache.
	/**
	 * 默认为DefaultConnectionFactory，机器数大于CLIENT_NUMBER用一致性哈希算法。
	 *
	 * @param cacheName
	 * @param ipStr
	 */
	public SpyMemcachedClient(String cacheName, String ipStr) {
		try {
			this.cacheName = cacheName;
			ConnectionFactory factory = new DefaultConnectionFactory();
			if (ipStr.split(" ").length >= CLIENT_NUMBER) {
				factory = new KetamaConnectionFactory();
			}
			client = new MemcachedClient(factory, AddrUtil.getAddresses(ipStr));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 *
	 * @param ipStr
	 * @param connectionFactory
	 *            All Known Implementing Classes: BinaryConnectionFactory,
	 *            DefaultConnectionFactory, KetamaConnectionFactory
	 */
	public SpyMemcachedClient(String ipStr, ConnectionFactory connectionFactory) {
		try {
			client = new MemcachedClient(connectionFactory, AddrUtil
					.getAddresses(ipStr));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void close() {
		client.shutdown();
	}

	/**
	 *
	 * @param key
	 * @param value
	 * @param secondLive
	 *            The actual value sent may either be Unix time (number of
	 *            seconds since January 1, 1970, as a 32-bit value), or a number
	 *            of seconds starting from current time.
	 * @return
	 */
	public void put(String key, CacheEntry<V> entry) {
		key = parseKey(key);
		if (key != null && entry != null) {
			try {
				int time = entry.getExpireTime() <= 0 ? 60*60*24*30
						: new Long(entry.getExpireTime()).intValue()/1000;
				client.set(key, time, entry);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public boolean exists(String key) {
		key = parseKey(key);

		if (key == null || key.isEmpty()) {
			return false;
		}
		if (get(key) == null) {
			return false;
		}
		return true;
	}

	public CacheEntry<V> getCacheEntry(String key) {
		return (CacheEntry<V>) get(key);
	}

	public CacheEntry<V> getCacheEntry(String key,int retryTimes) {
		return (CacheEntry<V>) get(key,retryTimes);
	}
	
	public void clear() {
		client.flush();
	}

	private Object get(String key) {
		key = parseKey(key);

		Object obj = null;
		if ( key != null ) {
			Future<Object> f = client.asyncGet(key);
			try {
				obj = f.get(3, TimeUnit.SECONDS);
			} catch (Exception e) {
				// Since we don't need this, go ahead and cancel the operation.
				// This is not strictly necessary, but it'll save some work on
				// the server.
				f.cancel(false);
				// Do other timeout related stuff
				logger.error("get timeout:" + key);
			}
		}
		return obj;
	}

	private Object get(String key,int retryTimes) {
		if(retryTimes==0){
			logger.error("retry timeout :" + key);
			return null;
		}
		key = parseKey(key);

		Object obj = null;
		if ( key != null ) {
			Future<Object> f = client.asyncGet(key);
			try {
				obj = f.get(3, TimeUnit.SECONDS);
			} catch (Exception e) {
				f.cancel(false);
				logger.error("get retry :" + key);
				get(key,retryTimes-1);
			}
		}
		return obj;
	}


	/**
	 * 一次取多个,批量取时推荐
	 * @param keys
	 * @return
	 */
	public Map<String, Object> asyncGetBulk(Collection<String> keys) {
		Map<String, Object> obj = null;
		if ( keys != null ) {
			Future<Map<String, Object>> f =  client.asyncGetBulk(keys);
			try {
				obj = f.get(30, TimeUnit.SECONDS);
			} catch (Exception e) {
				// Since we don't need this, go ahead and cancel the operation.
				// This is not strictly necessary, but it'll save some work on
				// the server.
				f.cancel(false);
				// Do other timeout related stuff
				logger.error("get timeout:" + keys);
			}
		}
		return obj;
	}

	public CacheStatus getStatus(String cacheName) {
		CacheStatus status = new CacheStatus();
		//String cacheName = this.cacheName;
		cacheName="["+this.cacheName+"]"+cacheName;
		long cacheSize = 0;
		long cacheCapture = 0;
		long total_items = 0;
		long hitCount = 0;
		long getCount = 0;
		long writeCount = 0;

		Map<SocketAddress, Map<String, String>> stats = client.getStats();
		for (Entry<SocketAddress, Map<String, String>> entry : stats.entrySet()) {
			Map<String, String> map = entry.getValue();

			cacheSize += Long.parseLong(map.get("bytes"));
			total_items += Long.parseLong(map.get("total_items"));
			hitCount += Long.parseLong(map.get("get_hits"));
			writeCount += Long.parseLong(map.get("cmd_set"));
			getCount += Long.parseLong(map.get("cmd_get"));
			cacheCapture += Long.parseLong(map.get("limit_maxbytes"));
		}

		status.setCacheName(cacheName);
		status.setCacheCapture(new Long(cacheSize /1024).intValue());
		status.setCacheSize(new Long(cacheCapture /1024).intValue());
		status.setExpireTime(total_items);
		status.setGetCount(getCount); 
		status.setHitCount(hitCount);
		status.setWriteCount(writeCount);
		return status;
	}

	private String parseKey(String key){
		try {
			key = URLEncoder.encode(key,"UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
		if(key.length()>250){
			key = Md5Util.MD5Encode(key);
		}
		return key;
	}

	@Override
	public void putCacheEntry(String key, CacheEntry<V> cacheent) {
		put(key,cacheent);
	}

	
	@Override
	public void put(String key, V obj) {
		putCacheEntry(key, new CacheEntry<V>(cacheExpiredTime, obj));
	}
	@Override
	public String getCname() {
		return "MEM_"+cacheName;
	}

	@Override
	public void clearAll() {
		//TODO
	}

	@Override
	public void clear(String key) {
		key = parseKey(key);

		if (key != null) {
			client.delete(key);
		}
	}
	
}