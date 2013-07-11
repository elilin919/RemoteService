package com.ling.remoteservice.msg.tcpimpl;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.cache.ICacheManager;
import com.ling.remoteservice.host.ServerData;
import com.ling.remoteservice.host.ServerIdentify;
import com.ling.remoteservice.msg.DataPack;

public class ConnectionPool extends Thread{
	public static byte[] datapackStart =new byte[]{'^','!','!','!','!','!','#'};
	public static byte[] datapackEnd   =new byte[]{'$','!','!','!','!','!','#'};
	public static byte[] datapackRegist=new byte[]{'@','!','!','!','!','!','#'};
	public static byte[] datapackHealth=new byte[]{'%','!','!','!','!','!','#'};
	
	static Log logger=LogFactory.getLog(ConnectionPool.class);
	final long baseCheckInterval=5000;
	long lastCheckTime=0;
	int checkErrorCount=0;
	
	ReadLock rlock;
	WriteLock wlock;
	ReentrantLock checklock;
	SocketManager socketManager;
	int connectionsPerServer,socketRecvBufSize,socketSendBufSize;
	ServerIdentify targetIdentify;
	String targetIdentifyStr;
	Map<String,ISocketProcesser> connections;
	Map<String,Integer> connCountByServer;
	boolean client=false;
	boolean stop  = false;
	boolean offLine=true;
	int reconnectConnectionTimes=0;
	
	BlockingQueue<DataPack> sendQueue;
	boolean needHealthCheck=false;
	//Thread checkConnectionThread=null;
	Random rd =  new Random();
	//AtomicInteger reconnectionSize;
	
	public ConnectionPool(SocketManager socketManager,String targetIdentifyStr,int connectionCount,int recvBuffer,int sendBuffer, boolean client){
		sendQueue = new ArrayBlockingQueue<>(350);
		connections=new HashMap<String,ISocketProcesser>();
		connCountByServer=new HashMap<>();
		ReentrantReadWriteLock rwlock=new ReentrantReadWriteLock();
		rlock=rwlock.readLock();
		wlock=rwlock.writeLock();
		checklock=new ReentrantLock();
		//reconnectionSize=new AtomicInteger(0);
		this.socketManager=socketManager;
		this.targetIdentifyStr=targetIdentifyStr;
		this.targetIdentify=new ServerIdentify(targetIdentifyStr);
		this.connectionsPerServer=connectionCount;
		this.socketRecvBufSize=recvBuffer;
		this.socketSendBufSize=sendBuffer;
		this.client=client;
		if (!client){
			//this is serversize, all the connections shall regist themself.
			offLine=false;
		}else{
			offLine=true;
			//初始化客户端连接
			checkConnections();
		}
		start(); //for writing data throw socket.
	}
	public String getTargetIdentify(){
		return targetIdentifyStr;
	}
	public void setStop(){
		this.stop=true;
		this.interrupt();
	}
	
	public void run(){
		
		while (!stop){
			ISocketProcesser processer=null;
			DataPack sendData=null;
			try {
				sendData=sendQueue.poll(30000, TimeUnit.MILLISECONDS);
				if (sendData==null)
					continue;
				processer=getRandomSocket();
				if (processer!=null){
					processer.sendData(sendData);
				}else{
					logger.error("There is no connected socket processers to send datapack: "+sendData.getTargetIdentify());
				}
			}catch(IOException e){
				if (sendData!=null && sendData.getParams()!=null && sendData.getParams().length>3){ 
					String clazzname=(String)sendData.getParams()[0];
					String iden=(String)sendData.getParams()[1];
					String sname=(String)sendData.getParams()[2];
					String sdesc=(String)sendData.getParams()[3];
					logger.error("sendData fault for execure ["+iden+"]"+sendData.getTargetIdentify()+"]["+clazzname+"."+sname+"."+sdesc, e);
				}
				if(sendData!=null && !ICacheManager.class.getName().equals(sendData.getParams()[0]))
					addSendData(sendData); //try to recover
				if (processer!=null) {
						String processerIdent=processer.getSocketIdentify();
						remove(processerIdent);
						processer.close();
				}
			}catch(Exception e){
				logger.error("",e);
			}
		}
	}
	
	public void put(String socketIdentify, ISocketProcesser processer) {
		try{
			wlock.lock();
			connections.put(socketIdentify, processer);
			logger.error("add a processer,current size["+connections.size()+"]:"+socketIdentify,new Exception("Caller StackTrace"));
			try{
				String remote=processer.getRemoteAddress().getHostString()+":"+processer.getRemoteAddress().getPort();
				Integer count=connCountByServer.get(remote);
				if (count==null) count=0;
				count=count+1;
				connCountByServer.put(remote, count);
				if (count>0) {
					offLine=false;
				}
			}catch(Exception e){
				logger.error("",e);
			}
		}finally{
			wlock.unlock();
		}
	}
	public void remove(String socketIdentify) {
		try{
			wlock.lock();
			logger.error("remove a processer,current size["+connections.size()+"]:"+socketIdentify,new Exception("Caller StackTrace"));
			ISocketProcesser processer=connections.remove(socketIdentify);
			if (client && processer!=null)
			try{
				String remote=processer.getRemoteAddress().getHostString()+":"+processer.getRemoteAddress().getPort();
				Integer count=connCountByServer.get(remote);
				if (count==null) count=0;
				count=count-1;
				connCountByServer.put(remote, count);
			}catch(Exception e){
				logger.error("",e);
			}
			if (connections.size()==0)
				offLine=true;
		}finally{
			wlock.unlock();
		}
	}
	public ISocketProcesser getRandomSocket(){
		int size=connections.size();
		if (size==0) return null;
		try{
			rlock.lock();
			String key=null;
			Set<String> keys=connections.keySet();
			if (size==1){
				key=keys.iterator().next();
			}else{
				int idx=Math.abs(rd.nextInt())%size;
				Iterator<String> kiter=keys.iterator();
				while (idx>-1){
					key=kiter.next();
					idx--;
				}
			}
			return connections.get(key);
		}finally{
			rlock.unlock();
		}
	}
	public List<ISocketProcesser> values() {
		try{
			rlock.lock();
			List<ISocketProcesser> list=new ArrayList<ISocketProcesser>();
			list.addAll(connections.values());
			return list;
		}finally{
			rlock.unlock();
		}
	}
	public void close(){
		for (ISocketProcesser p:values()){
			p.close();
		}
	}
	public synchronized void checkConnections(){
		if (!client) return;
		long current=System.currentTimeMillis();
		if (lastCheckTime==0 || (current-lastCheckTime)>baseCheckInterval){
			logger.info("try check connections for "+targetIdentifyStr+".");
			//lastCheckTime.put(key, current+baseCheckInterval*5);  //链接成功，6倍检查时间
			lastCheckTime = current+baseCheckInterval*5;
			boolean offline=checkOffline();
			if (offline){ //服务端离线
				checkErrorCount++;
				if (checkErrorCount<20){
					lastCheckTime=current+baseCheckInterval*3; //4倍时间
				}else{
					if (checkErrorCount<100){
						lastCheckTime=current+baseCheckInterval*10; //11倍检查时间
					}else{
						lastCheckTime=current+baseCheckInterval*500; //放弃链接
					}
				}
			}else{
				checkErrorCount=0;
			}
		}
		
	}
	private boolean checkOffline() {
		boolean allsuccess=true;
		ServerData[] servers=targetIdentify.getServers();
		//connectionsPerServer shall be 1, but that must depands on config.
		int checksize=servers.length * connectionsPerServer - connections.size(); //=0时，总连接数已达到
		if (checksize>0){ 
			int size_per_server= connectionsPerServer ;///servers.length;
			for (ServerData sd:servers){
				String remote=sd.getHost()+":"+sd.getPort();
				Integer count=connCountByServer.get(remote);
				if (count==null) count=0;
				int size=size_per_server-count;
				if (size>0){
					logger.error("Try create ["+size+"] connection to "+remote+", target connection count should be :"+size);
					for (int i=0;i<size ;i++){
						boolean success = createConnection(targetIdentifyStr,sd);
						if (!success) allsuccess=false;
						if (!success) break;
					}
				}
			}
		}
		if (!allsuccess && connections.size()==0){
			logger.error("Server "+targetIdentify+" is off line.");
			offLine=true;
		}else{
			offLine=false;
		}
		return offLine;
	}
	public boolean isOffLine(){
		return offLine;
	}
	private boolean createConnection(String targetIdentify,ServerData serverdata){
		boolean connectionFault=false;
		logger.info("try remote ["+reconnectConnectionTimes+"] connections to ["+targetIdentify+"]");
		try {
			SocketChannel channel = connect(targetIdentify,serverdata,socketRecvBufSize,socketSendBufSize);
			ISocketProcesser processer = new SocketProcesser(socketManager, this, channel,true); //client status,need regist in server.
			put(processer.getSocketIdentify(),processer);
			connectionFault=false;
			logger.info("connected to ["+targetIdentify+"]:["+processer.getSocketChannel()+"]");
		} catch (ConnectException e) {
			logger.error("connected to ["+targetIdentify+"] fault");
			connectionFault=true;
		} catch (IOException e) {
			connectionFault=true;
		}
		return !connectionFault;
		
	}
	private SocketChannel connect(String targetIdentify,ServerData targetServer,int recvbuff,int sendbuff) throws IOException{
		SocketChannel socketChannel=null;
		//this.targetServe=targetIdentify.toString();
		InetSocketAddress addr=new InetSocketAddress(targetServer.getHost(),targetServer.getPort());
		//logger.info("remote server addr:"+addr);
		//remoteAddress=addr;
		try {
			socketChannel=SocketChannel.open();			
			socketChannel.socket().setReceiveBufferSize(recvbuff);
			socketChannel.socket().setSendBufferSize(sendbuff);
			//socketChannel.socket().setKeepAlive(true);
			//立刻发送数据包
			socketChannel.socket().setTcpNoDelay(true);
			//强制关闭
			socketChannel.socket().setSoLinger(true, 0);
			socketChannel.socket().setSoTimeout(3000);
			socketChannel.socket().setPerformancePreferences(1, 3, 2);
			//socketChannel.socket().setOOBInline(on);
			//socketChannel.socket().setTcpNoDelay(true);
			logger.info("connect:"+addr);
			socketChannel.connect(addr);
			socketChannel.configureBlocking(true);
			boolean connected=socketChannel.finishConnect();
			socketChannel.configureBlocking(false);
			if (!connected){
				logger.error("can't connected to server:"+addr);
			}
			int count=0;
			while (socketChannel.isConnectionPending()){
				socketChannel.finishConnect();
				try {
					Thread.sleep(100);
					count++;
					if (count>100){
						logger.error("can't connected to server .");
						break;
					}
				} catch (InterruptedException e) {
					logger.error("",e);
				}
			}
		} catch (IOException e) {
			logger.error("",e);
			throw e;
		}
		return socketChannel ;
	}
	public boolean addSendData(DataPack dp) {
		if (isOffLine()){//目标服务器不可用
			if (sendQueue.size()>100) //100个发送队列内，正在连接的状态。
				return false;
		}
		//添加待发送数据
		//BlockingQueue<DataPack> sendQueue=getSendQueue(dp.getTargetIdentify().toString());
		boolean added= sendQueue.offer(dp);
		if (!added)
			logger.error("Send Queue is full .fault add for target :"+dp.getTargetIdentify());
		return added;
	}

	public boolean isClient() {
		return client;
	}
}
