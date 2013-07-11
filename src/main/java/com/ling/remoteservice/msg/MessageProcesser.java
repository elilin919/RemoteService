package com.ling.remoteservice.msg;

import java.net.SocketException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.cache.ICacheManager;
import com.ling.remoteservice.host.ClientIdentify;

public class MessageProcesser {

	
	static Log logger =LogFactory.getLog(MessageProcesser.class);

	Map<String, MessageListener[]> listenerStatusClient;
	Map<String, MessageListener[]> listenerStatusServer;

	Processer[] processers;

	boolean isclose = false;

	AtomicInteger processerCount;

	int threadsProcess = 15;
	ClientIdentify clientIdentify;
	Messager messager;
	
    //Map<String, Map<String,ClientIdentify>> remoteHosts;
    //Map<String,ClientIdentify> allRemote;
    Lock rlock,wlock,rlock2,wlock2;
    String cacheListenKey=ICacheManager.class.getName();
    
	public MessageProcesser(Messager messager, int processCount) throws SocketException {
		//Configure conf=new Configure("messager.properties");
		this.messager = messager;
		//remoteHosts = new HashMap<String, Map<String,ClientIdentify>>();
        //allRemote   = new HashMap<String, ClientIdentify>();
        initLocks();
		processerCount=new AtomicInteger(0);
		listenerStatusClient = new ConcurrentHashMap<String, MessageListener[]>();
		listenerStatusServer = new ConcurrentHashMap<String, MessageListener[]>();
		threadsProcess = processCount;//conf.getInt("local.Threads_Processer", threadsProcess);
		startThreads();
	}

	private void initLocks() {
		ReentrantReadWriteLock rwlock=new ReentrantReadWriteLock();
        ReentrantReadWriteLock rwlock2=new ReentrantReadWriteLock();
        rlock=rwlock.readLock();rlock2=rwlock2.readLock();
        wlock=rwlock.writeLock();wlock2=rwlock2.writeLock();
	}
	
	protected void startThreads() {
		processers = new Processer[threadsProcess];
		for (int i = 0; i < processers.length; i++) {
			processers[i] = new Processer();
			processers[i].setName("processer[" + i + "]");
			processers[i].start();
		}
	}
	public void addListener(MessageListener mlistener, int status) {
		if (mlistener != null) {
			String listenerType = "Server";
			Map<String, MessageListener[]> listeners = null;
			if (status == Messager.LISTENER_TYPE_CLIENT) {
				listeners = listenerStatusClient;
				listenerType = "Client";
			}
			if (status == Messager.LISTENER_TYPE_SERVER)
				listeners = listenerStatusServer;

			String cmd = mlistener.getListenChannel();
			MessageListener[] s = listeners.get(cmd);
			logger.error("add " + listenerType + " listener  :" + cmd);
			if (s == null) {
				listeners.put(cmd, new MessageListener[] { mlistener });
			} else {
				MessageListener[] nlist = new MessageListener[s.length + 1];
				System.arraycopy(s, 0, nlist, 0, s.length);
				nlist[s.length] = mlistener;
				listeners.put(cmd, nlist);
			}
		}
	}

	public void removeListener(MessageListener mlistener) {

		String cmd = mlistener.getListenChannel();
		logger.info("try remove listener :" + cmd);
		// Map<String, MessageListener[]> listeners=null;
		removeListener(mlistener, cmd, listenerStatusServer);
		removeListener(mlistener, cmd, listenerStatusClient);
	}

	private void removeListener(MessageListener mlistener, String cmd, Map<String, MessageListener[]> listeners) {
		MessageListener[] s = listeners.get(cmd);
		if (s != null) {
			int idx = -1;
			for (int i = 0; i < s.length; i++) {
				if (s[i] == mlistener) {
					idx = i;
					// logger.info("listener index:" + idx);
				}
			}
			if (s.length == 1) {
				if (idx == 0) {
					listeners.remove(cmd);
					// logger.info("listener remove:" + cmd);
				}
			} else {
				if (idx != -1) {
					MessageListener[] nlist = new MessageListener[s.length - 1];
					System.arraycopy(s, 0, nlist, 0, idx);
					System.arraycopy(s, idx + 1, nlist, idx, nlist.length - idx);
					// nlist[s.length]=mlistener;
					// logger.info("listener size:" + nlist.length);
					listeners.put(cmd, nlist);
				}
			}
		}
	}
	
	public boolean close() {
		isclose = true;
		// TODO notify server if client close;
		for (MessageListener[] l : listenerStatusClient.values()) {
			for (MessageListener m : l)
				try {
					m.close();
				} catch (Exception e) {
					logger.error("", e);
				}
		}
		for (MessageListener[] l : listenerStatusServer.values()) {
			for (MessageListener m : l)
				try {
					m.close();
				} catch (Exception e) {
					logger.error("", e);
				}
		}
		return true;

	}

	class Processer extends Thread {
		
		// static Log logger=LogFactory.getLog(ReceiveProcesser.class);
		public void run() {
			while (!isclose) {
				try {
					DataPack dp = null;
					processerCount.incrementAndGet();
					dp = messager.getMsg();// should block here.
					int cnt=processerCount.decrementAndGet();
					if (cnt==0)
						logger.error("current processer count is 0!");
					if (dp != null) {
//						if (dp.getRecvPort() != 0)
//							dp.setPort(dp.getRecvPort()); 
						
						if ("localhost".equals(dp.getSourceIdentify().getHost().toLowerCase())){
							dp.getSourceIdentify().setHost(dp.getSourceAddress().getHostString());
							//InetSocketAddress raddr= (InetSocketAddress) dp.getIneraddr().getHostAddress();
							//remoteIdent=remoteIdent.substring(0, hostsidx+3)+raddr.getHostString()+remoteIdent.substring(hosteidx);
							logger.info("SourceIdentify has change to ["+dp.getSourceIdentify()+"] data pack ["+dp.getKey()+"]");
						}
						String client = dp.getSourceIdentify().toString();

						
						int status = dp.getStatus();
						// logger.debug("receive
						// data:"+BytesUtils.joinBytes(dp.getData()));
						logger.info("process data:[" + dp.getKey() + "]sentDate["+new Date(dp.getRetryKey())+"]for[" + dp.getSourceIdentify() + "-->"+dp.getTargetIdentify()+"]");

						// String key=(String) parseListenerKey(dp.getData());
						// ClassLoader listenerClassLoader=null;
						// try{
						// listenerClassLoader=DLoader.getInstance().loadClass(key).getClassLoader();
						// }catch(ClassNotFoundException e){
						// }

						Object[] objs = dp.getParams();//parseByteData(null, dp.getData(), dp.getVersion());
						if (objs[0] instanceof String) {
							// boolean isprocessed=false;
							String listenerKey = (String) objs[0];
							// ClientIdentify ci=new ClientIdentify();
							if (!cacheListenKey.equals(listenerKey)) //非缓存更新的命令，注册远程服务器
								messager.addRegistHost((String)objs[1], client);

							Map<String, MessageListener[]> listeners = null;
							String listenerType = "Server_request";
							if (status == DataPack.STATUS_REQUEST) {
								listeners = listenerStatusServer;
							}
							if (status == DataPack.STATUS_RESPONSE) {
								listeners = listenerStatusClient;
								listenerType = "Client";
							}
							logger.info("dpkey[" + dp.getKey() + "] datapack type [" + (status == DataPack.STATUS_RESPONSE ? "response" : "request") + "] listener type[" + listenerType + "]:"
									+ listenerKey);
							boolean isProcessed = false;
							Object[] paramObjs = objs;
							MessageListener[] mlistener = listeners.get(listenerKey);
							if (mlistener != null)
								// isprocessed=true;
								for (int i = 0; i < mlistener.length; i++) {
									if (mlistener[i] != null)
										try {
											logger.info("dispatch package:" + listenerKey + ":" + mlistener[i].getClass().getName());
											mlistener[i].process(dp, paramObjs);
											isProcessed = true;
										} catch (Exception e) {
											logger.error("", e);
										}
								}
							if (!isProcessed)
								logger.error("No match listener for DataPack :" + dp.getKey() + ":"+listenerKey+" ");
							// default listener
							mlistener = (MessageListener[]) listeners.get("*");
							if (mlistener != null)
								for (int i = 0; i < mlistener.length; i++) {
									if (mlistener[i] != null)
										try {
											mlistener[i].process(dp, paramObjs);
										} catch (Exception e) {
											logger.error("", e);
										}
								}
						} else {
							logger.error("not a channel name.");
						}
					}
				} catch (Exception e) {
					logger.error("", e);
				}
			}
		}

	}
	
	/*
    private void addRegistHost(String serviceName, String sourceIdentify) {
    	Map<String,ClientIdentify> serviceMap=getServiceMap(serviceName);
    	ClientIdentify ci=null;
    	try{
    		rlock2.lock();
    		ci=serviceMap.get(sourceIdentify);
    	}finally{
    		rlock2.unlock();
    	}
    	if (ci==null){
    		try{
    			wlock2.lock();
    			if (serviceMap.get(sourceIdentify)==null){
    				ci=new ClientIdentify(sourceIdentify);
    				serviceMap.put(sourceIdentify, ci);
    				allRemote.put(sourceIdentify, ci);
    				logger.error("host-regist: ["+serviceName+"]@"+sourceIdentify);
    				logRemoteRegistStatus();
    			}
    		}finally{
    			wlock2.unlock();
    		}
    	}
    }
    
	private Map<String, ClientIdentify> getServiceMap(String service) {
    	Map<String, ClientIdentify> smap=null;
        try{
        	rlock.lock();
        	smap=remoteHosts.get(service);
        }finally{
        	rlock.unlock();
        }
        if (smap==null)
        try{
        	wlock.lock();
        	smap=remoteHosts.get(service);
        	if (smap==null){
        		smap=new HashMap<String, ClientIdentify>();
        		remoteHosts.put(service, smap);
        	}
        }finally{
        	wlock.unlock();
        }
        return smap;
	}
    private void logRemoteRegistStatus() {
    	StringBuffer res=new StringBuffer();
		for (Entry<String,Map<String,ClientIdentify>> ent:remoteHosts.entrySet()){
			res.append("\nservice:"+ent.getKey()+"\n");
			for (Entry<String, ClientIdentify> ent2 : ent.getValue().entrySet()){
				res.append("    ").append(ent2.getKey());
			}
		}
		logger.error("HOST STATUS:\n"+res);
	}
	*/
}
