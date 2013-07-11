package com.ling.remoteservice.msg;

import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.Configure;
import com.ling.remoteservice.host.ClientIdentify;
import com.ling.remoteservice.host.ServerIdentify;
import com.ling.remoteservice.msg.tcpimpl.TCPDatapackHandel;

class MessagerImpl implements Messager {
	static Log logger=LogFactory.getLog(MessagerImpl.class);
	
	Properties props;
	Map<String,ServerIdentify> servers;
    BlockingQueue<DatagramPacket> datagramRecvBuffer;
    NetFlowListener netWatcher;
    Random rd;
    Lock rlock,wlock,rlock2,wlock2;
    UncompleteGramBuffer buffer;
    Map<String, Map<String,ClientIdentify>> remoteHosts;
    Map<String,ClientIdentify> allRemote;
    DatapackHandel tcpDpHandel; //updDpHandel,
    MessageProcesser processer;
    Configure conf;
    
    public MessagerImpl() throws SocketException {
    	Configure conf = new Configure("messager.properties");
        remoteHosts = new HashMap<String, Map<String,ClientIdentify>>();
        allRemote   = new HashMap<String, ClientIdentify>();
        buffer = UncompleteGramBuffer.getInstance();
        ReentrantReadWriteLock rwlock=new ReentrantReadWriteLock();
        ReentrantReadWriteLock rwlock2=new ReentrantReadWriteLock();
        netWatcher=new NetFlowListener();
        rlock=rwlock.readLock();rlock2=rwlock2.readLock();
        wlock=rwlock.writeLock();wlock2=rwlock2.writeLock();
        rd = new Random();      
        servers=new ConcurrentHashMap<>();
        
        this.conf = conf;
        initSocket(conf);
        
        processer = new MessageProcesser(this, conf.getInt("local.threads.processer", 30));

        initServerConfig();
    }

    
	private void initServerConfig(){
		//config = new Configure("serverlist.properties",);
        props=new Properties();
        try{
            InputStream ins=Thread.currentThread().getContextClassLoader().getResourceAsStream("serverlist.properties");
            if (ins!=null){
                props.load(ins);
                ins.close();
            }else{
                logger.error("can't find serverlist.properties!");
            }
        }catch(Exception e){}
        
	}
	public List<String>  getAllServer(){
	    List<String> list=new ArrayList<String>();
	    Set<Object> ks=props.keySet();
	    for (Object k:ks){
	        list.add((String)k);
	    }
	    return list;
	}
	public ServerIdentify getServer(String servicename){
	    if (!servers.containsKey(servicename)){
	    	String configstr=props.getProperty(servicename);
	    	if (configstr==null){
	    		servers.put(servicename,null);
	    		return null;
	    	}
	        ServerIdentify s=new ServerIdentify(configstr);
	        servers.put(servicename,s);
	        //MessagerFactory.getMessager().addSendData(makeRegistData(s));
	    }
	    return servers.get(servicename);
	}
	
    public void initSocket(Configure params) throws SocketException {
    	int recvBufferSize=params.getInt("local.recvQueueSize",1000);
        datagramRecvBuffer=new ArrayBlockingQueue<DatagramPacket>(recvBufferSize);

        tcpDpHandel=new TCPDatapackHandel();
        tcpDpHandel.setMessager(this);
        tcpDpHandel.initSocket(params);
    }

    public boolean isHealth() {
        return true;
    }
    
    public DataPack getMsg(){
    	DatagramPacket p=null;
		try {
			p = datagramRecvBuffer.take();
			if (rd.nextInt()%500==1 && datagramRecvBuffer.size()>0){
			    logger.error("received datagrampack buffer size:"+datagramRecvBuffer.size());
			}
		} catch (InterruptedException e) {
		}
		DataPack dp=buffer.addDataGram(p);
		if (dp!=null) {
			if ("localhost".equals(dp.getSourceIdentify().getHost().toLowerCase())){
				dp.getSourceIdentify().setHost(dp.getSourceAddress().getHostString());
				logger.info("SourceIdentify has change to ["+dp.getSourceIdentify()+"] data pack ["+dp.getKey()+"]");
			}
			netWatcher.logReceivedFlow(dp);
		}
		return dp;
    }
	@Override
	public void addRecvData(DatagramPacket dgPack) {
		boolean added=datagramRecvBuffer.offer(dgPack);
		if (!added)
			logger.error("recv buffer is full.");
	}

    public ClientIdentify getLocalIdentify(ServerIdentify target){
    	//依据不同的类型调用不同的datahandel
    	//if (target!=null)
    	//if (Identify.PROTOCOL_TCP.equals(target.getProtocol()))
    	return tcpDpHandel.getLocalIdentify();
    	//return updDpHandel.getIdentify();
    	//return null;
    }
    
    public void sendMsg(ServerIdentify target,ClientIdentify source,int status,Object[] senddata) {
    	DataPack dp=tcpDpHandel.makeSendData(target,source,status,senddata);
    	//addSendData(dp);
    	//ServerIdentify target=dp.getTargetIdentify();
    	netWatcher.logSentFlow(dp);
    	tcpDpHandel.addSendData(dp);
    }
    public DataPack makeSendData(ServerIdentify target,ClientIdentify source,int status, Object[] data){
    	DataPack dp=tcpDpHandel.makeSendData(target,source,status,data);
    	return dp;
    }
    public boolean addSendData(DataPack dp){
    	netWatcher.logSentFlow(dp);
    	return tcpDpHandel.addSendData(dp);
    }

    public void addRegistHost(String serviceName, String sourceIdentify) {
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

	public boolean close() {
		processer.close();
        return tcpDpHandel.close();
    }

    public Map<String, ClientIdentify> getAllRemoteHosts(String serviceName) {
    	if (serviceName!=null)
    		return getServiceMap(serviceName);
    	return null;
    }
    
    public void addListener(MessageListener listener, int listenerTypeServer){
    	processer.addListener(listener, listenerTypeServer);
    }
}
