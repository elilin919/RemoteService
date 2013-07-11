package com.ling.remoteservice.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.collections.map.LRUMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.Manager;
import com.ling.remoteservice.host.ClientIdentify;
import com.ling.remoteservice.host.ServerIdentify;
import com.ling.remoteservice.msg.DataPack;
import com.ling.remoteservice.msg.MessageListener;
import com.ling.remoteservice.msg.Messager;
import com.ling.remoteservice.msg.MessagerFactory;

public class CacheSyncListener implements MessageListener {

	static Log logger=LogFactory.getLog(CacheSyncListener.class);
	ICacheManager cachemgr;
	Map<String, Boolean> clearCommandcache;
	Lock clearCacheLock;
	Messager messager;
	Map<String,Map<String,ClientIdentify>> synchosts;
	
	public CacheSyncListener(ICacheManager cachemgr, String[] synchosts) {
		this.cachemgr = cachemgr;
		this.synchosts=parseSyncConfig(synchosts);
		clearCacheLock = new ReentrantLock();
		clearCommandcache = new LRUMap(200);
		messager = MessagerFactory.getInstance();
	}

	private Map<String,Map<String, ClientIdentify>> parseSyncConfig(String[] synchosts) {
		Map<String, Map<String,ClientIdentify>> res=new HashMap<>();
		
		if (synchosts != null) 
		for (String hostconf:synchosts){
			
			//taoban-dao@TCP://172.168.1.110:10003
			int idx=hostconf.indexOf('@');
			if (idx!=-1){
				String service=hostconf.substring(0,idx);
				String ident  =hostconf.substring(idx+1);
				ClientIdentify identify = new ClientIdentify(ident);
				Map<String, ClientIdentify> mtemp = res.get(service);
				if (mtemp==null) {
					mtemp=new HashMap<>();
					res.put(service, mtemp);
				}
				mtemp.put(ident, identify);
			}
		}
		return res;
	}

	@Override
	public String getListenChannel() {
		return CacheSyncListener.class.getName();
	}

	@Override
	public void close() {
		
	}

	public void sendClear(String serviceName, String cacheName, String cacheKey, String fsource, int times) {
		Map<String, ClientIdentify> mtemp = getCacheNotifyServer(serviceName);

		if (mtemp != null && mtemp.size() > 0) {
			Map<String, ClientIdentify> m = new HashMap<String, ClientIdentify>();
			m.putAll(mtemp);

			String allsource = makeAllSource(fsource, m);
			
			Object[] data = new Object[] { getListenChannel(), allsource, times, cacheName, cacheKey, serviceName };

			long dpkey = DataPack.genKey();
			clearCacheLock.lock();
			try {
				clearCommandcache.put(dpkey + "", true);
			} finally {
				clearCacheLock.unlock();
			}

			for (ClientIdentify ci : m.values()) {
				// logger.info("send clear");
				sendRemoteClientClear(cacheName, cacheKey, ci, dpkey, data);
			}
		}
	}

	private void sendRemoteClientClear(String cacheName, String cacheKey, ClientIdentify ci, long dpkey, Object[] data) {

		ServerIdentify target = new ServerIdentify(ci.toString());
		ClientIdentify source = messager.getLocalIdentify(target);
		DataPack dp = messager.makeSendData(target, source, DataPack.STATUS_REQUEST, data);
		if (dpkey != 0)
			dp.setKey(dpkey);
		logger.info("send clear Cache to client:" + ci.toString() + "/" + cacheName + "/" + cacheKey + ":" + dp.getKey());

		messager.addSendData(dp);
	}

	// @Override
	// 判断是否需要通知其它机器
	// 1.客户端非远程执行时，会发送消息至当前机器。
	// 2.客户端是远程执行时，server端在执行时，是!remoteModel状态，会发送消息至相关服务器，要求清除缓存。
	// 收到消息时，
	public void process(DataPack datapack, Object[] params) {
		// String channel=(String) params[0];
		long dpkey = datapack.getKey();
		try {
			clearCacheLock.lock();
			Boolean isProcessed = clearCommandcache.get(dpkey + "");
			if (isProcessed != null) {
				logger.info("clear command [" + dpkey + "] is processed.");
				return;
			} else {
				clearCommandcache.put(dpkey + "", true);
			}
		} finally {
			clearCacheLock.unlock();
		}
		String serviceName = null;
		String source = null;
		int times = 0; // just false...
		String cachename = null;
		String cachekey = null;
		if (params.length > 4) {
			// serviceName=(String) params[1];
			source = (String) params[1];
			times = (Integer) params[2]; // just false...
			cachename = (String) params[3];
			cachekey = (String) params[4];
		}
		if (params.length > 5)
			serviceName = (String) params[5];
		if (cachename != null && cachekey != null) {
			String comefrom = "cache clear from " + datapack.getSourceIdentify();
			// netWatcher.logReceivedCacheCommandCount(datapack.getSourceIdentify().toString());
			logger.info("Receive CacheClear[" + source + "][" + times + "]:" + cachename + ":" + cachekey + "@" + serviceName + " from [" + comefrom + "]:dpkey:" + datapack.getKey());
			if (times >= 3) {
				// check
				// logger.info("local identify ["+local+"] is in
				// source:"+source);
				return;
			}
			cachemgr.clearCache(null, cachename, cachekey, true);
			if (serviceName != null)
				notifyAllRemoteHost(serviceName, dpkey, source, times, cachename, cachekey);
		}
	}

	private void notifyAllRemoteHost(String serviceName, long dpkey, String fsource, int times, String cachename, String cachekey) {
		Map<String, ClientIdentify> mtemp = getCacheNotifyServer(serviceName);
		if (mtemp != null && mtemp.size() > 0) {
			Map<String, ClientIdentify> m = new HashMap<String, ClientIdentify>();
			m.putAll(mtemp);

			String allsource = makeAllSource(fsource, m);
			times++;
			//
			Object[] data = new Object[] { getListenChannel(), allsource, times, cachename, cachekey, serviceName };
			for (ClientIdentify cci : m.values()) {
				String remotekey = cci.getHost() + ":" + cci.getPortStr();
				if (fsource.indexOf(remotekey) == -1) {
					// netWatcher.logSentCacheCommandCount(cci.getHost());
					sendRemoteClientClear(cachename, cachekey, cci, dpkey, data);
				} else
					logger.info("remote host:" + remotekey + " is in source:" + fsource);
			}
		}
	}

	private Map<String, ClientIdentify> getCacheNotifyServer(String serviceName) {
		Map<String, ClientIdentify> mtemp = messager.getAllRemoteHosts(serviceName);
		if (synchosts != null) {
			Map<String, ClientIdentify> cusCfg = synchosts.get(serviceName);
			if (cusCfg != null)
				mtemp.putAll(cusCfg);
		}
		return mtemp;
	}

	private String makeAllSource(String fsource, Map<String, ClientIdentify> m) {
		StringBuffer res = new StringBuffer();
		if (fsource != null && fsource.length() > 0)
			res.append(fsource);

		Map<String, String> localIdent = new HashMap<String, String>();
		res.append("{");
		for (ClientIdentify cci : m.values()) {
			String key = cci.getHost() + ":" + cci.getPortStr();
			// if (fsource==null || fsource.indexOf(key)==-1){
			// res.append("|");
			// res.append(key);
			// }
			if (localIdent.get(key) == null) {
				localIdent.put(key, "");
				ServerIdentify targetIdent = new ServerIdentify(cci.toString());
				ClientIdentify client = messager.getLocalIdentify(targetIdent);
				String keySource = client.getHost() + ":" + client.getPortStr();
				localIdent.put(keySource, "");
			}
		}
		for (Entry<String, String> ent : localIdent.entrySet()) {
			res.append(ent.getKey()).append("|");
		}
		res.append("}");
		return res.toString();
	}

}
