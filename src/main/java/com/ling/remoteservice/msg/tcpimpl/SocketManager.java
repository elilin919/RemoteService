package com.ling.remoteservice.msg.tcpimpl;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.Configure;
import com.ling.remoteservice.host.ClientIdentify;
import com.ling.remoteservice.msg.DataPack;
import com.ling.remoteservice.msg.DatapackHandel;

public class SocketManager extends Thread{
	final long baseCheckInterval=5000;
	static Log logger = LogFactory.getLog(SocketManager.class);
	
	AcceptThread acceptThread;
	//KeepAliveThread keepAliveThread;
	//KeepConnectThread keepConnectThread;
	
	DatapackHandel dpHandel;
	int connectionsPerServer;
	
	ServerSocketChannel serverChannel;
	int socketRecvBufSize,socketSendBufSize;
	
	int readSelectorSize;
	Selector[] readSelectors;
	SelectorReadThread[] readThreads;
	
	Map<String,ConnectionPool> serverConnPools;
	//Map<String,BlockingQueue<DataPack>> sendQueues;
	
	
	AtomicInteger selectorIdx;
	ReadLock rlock;
	WriteLock wlock;
	ReentrantLock lock;
	boolean stop=false;
	//KeepClientConnectionsThread keepConnThread;
	
	public SocketManager(DatapackHandel datapackHandel, Configure params) {
		this.dpHandel=datapackHandel;

		connectionsPerServer=params.getInt("local.tcp-server.connectionsPerServer", 1);
		socketRecvBufSize   =params.getInt("local.tcp-server.socket.sendBufferSize", 1024*1024);
		socketSendBufSize   =params.getInt("local.tcp-server.socket.receiveBufferSize", 1024*1024);
		readSelectorSize    =params.getInt("local.tcp-server.read.selector.size", 5);
		selectorIdx=new AtomicInteger(0);
		
		//sendQueues=new HashMap<String, BlockingQueue<DataPack>>();
		
		serverConnPools=new HashMap<String, ConnectionPool>();
		//keepConnThread=new KeepClientConnectionsThread();
		//keepConnThread.start();
		
		initReadSelector();
		
		ReentrantReadWriteLock rwlock = new ReentrantReadWriteLock();
		rlock = rwlock.readLock();
		wlock = rwlock.writeLock();
		lock=new ReentrantLock();
	}
	private void initReadSelector(){
		readSelectors=new Selector[readSelectorSize];
		readThreads = new SelectorReadThread[readSelectorSize];
		for (int i=0;i<readSelectorSize;i++){
			try {
				readSelectors[i]=Selector.open();
				readThreads[i]=new SelectorReadThread(this,readSelectors[i]);
				readThreads[i].start();
			} catch (IOException e) {
				logger.error("",e);
			}
		}
	}
	public int initServer(String serverHost, int serverPort) {
		//int bindServerPort=serverPort;
		try {
			
			Selector selector=null;
			logger.debug("acceptor started...");
			selector = Selector.open();
			serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(false);
			serverChannel.socket().setReuseAddress(false);
			serverChannel.socket().setReceiveBufferSize(socketRecvBufSize);
			serverChannel.socket().setPerformancePreferences(0, 2, 1);
			serverChannel.socket().setSoTimeout(3000);
			for (int i=0;i<10;i++){
				try{
					serverPort=(serverPort+i);
					serverChannel.socket().bind(new InetSocketAddress(serverHost, serverPort));
					if (serverChannel.socket().isBound()){
						//serverChannel.is
						logger.error("Bind Successful:"+serverHost+":"+serverPort);
						break;
					}else{
						logger.error("Cannot bind:"+serverHost+":"+serverPort);
					}
				}catch(IOException e){
					logger.error("Cannot bind:"+serverHost+":"+serverPort,e);
				}
			}
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
			
			acceptThread=new AcceptThread(this,selector);
			acceptThread.start();
		} catch (Exception e) {
			logger.error("Cannot bind:"+serverHost+":"+serverPort,e);
		}
		return serverPort;
	}

	public boolean addSendData(DataPack dp){
		//创建连接池对象
		ConnectionPool pool=getProcesserList(dp.getTargetIdentify().toString());
		if (pool==null) //没有连接服务器。以客户端方式，连接服务器
			pool=createProcesserList(dp.getTargetIdentify().toString(),true);
		return pool.addSendData(dp);
	}
	
	public void unRegistConnectionPool(String targetIdentify){
		try{
			wlock.lock();
			serverConnPools.remove(targetIdentify);
		}finally{
			wlock.unlock();
		}
	}
	public ConnectionPool getProcesserList(String targetIdentify){
		ConnectionPool conp=null;
		try{
			rlock.lock();
			conp=serverConnPools.get(targetIdentify);
		}finally{
			rlock.unlock();
		}
		return conp;
	}
	public ConnectionPool createProcesserList(String targetIdentify,boolean client){
		ConnectionPool conp=null;
		try{
			wlock.lock();
			conp=serverConnPools.get(targetIdentify);
			if (conp==null){ 
				conp=new ConnectionPool(this,targetIdentify,connectionsPerServer,socketRecvBufSize,socketSendBufSize,client);
				logger.info("ConnectionPool for ["+targetIdentify+"] was added.");
				serverConnPools.put(targetIdentify, conp);
			}
		}finally{
			wlock.unlock();
		}
		return conp;
	}
	
	public void addRecvData(DatagramPacket dp){
		dpHandel.addRecvData(dp);
	}
	
	public ClientIdentify getLocalIdentify(){
		return dpHandel.getLocalIdentify();
	}


	class AcceptThread extends Thread{
		Selector selector;
		boolean stop=false;
		SocketManager socketManager;
		public AcceptThread(SocketManager socketManager,Selector selector) {
			this.socketManager=socketManager;
			this.selector=selector;
		}

		public void run() {
			while (!stop) {
				int events = 0;
				try {
					events = selector.select();
				} catch (IOException e) {
					logger.error(e);
				}
				if (events > 0) {
					Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
					while (iter.hasNext()) {
						SelectionKey key = iter.next();
						if (key.isValid() && key.isAcceptable()) {
							ServerSocketChannel readyChannel = (ServerSocketChannel) key.channel();
							SocketChannel socketChannel = null;
							try {
								socketChannel = (SocketChannel) readyChannel.accept();
								socketChannel.socket().setReceiveBufferSize(socketRecvBufSize);
								socketChannel.socket().setSendBufferSize(socketSendBufSize);
								//socketChannel.socket().setTcpNoDelay(true);
								socketChannel.configureBlocking(false);
								//socketChannel.socket().setSendBufferSize(socketSendBufSize);
								logger.info("accept new connection:"+socketChannel.socket().toString());
								//addConnection(socketChannel);
								/*
								 * 接受一个来自客户端的链接
								 * 注册selector
								 * 并读取注册数据包，
								 * 依据第一个数据包指定的clientIdentify，将自身(SocketProcesser)置入指定的ConnectionPool对象中。
								 * 	
								 * 在reader中调用该接口，将接收到目标端口发送的注册远程服务信息的处理器启动写线程
								 */
								new SocketProcesser(socketManager,null,socketChannel,false); 
							} catch (IOException e) {
								logger.error(e);
							}
						}
						iter.remove();
					}
				}
			}
			
		}
		public void setStop(){
			stop=true;
		}
	}
	
	public SelectorReadThread getSelector(){
		int current=selectorIdx.incrementAndGet();
		return readThreads[current%readThreads.length];
	}
	
	public void close() {
		stop=true;
		closeServerSocket();
		for (ConnectionPool ss:serverConnPools.values()){
			ss.close();
		}
		
	}
	private void closeServerSocket() {
		acceptThread.setStop();
		try {
			logger.error("server scocket closed:"+serverChannel);
			serverChannel.close();
		} catch (IOException e) {
			logger.error("",e);
		}
	}

	public void run(){
		while (!stop)
		try{
			try{
				rlock.lock();
				for (String key:serverConnPools.keySet()){
					ConnectionPool pool=serverConnPools.get(key);
					if (pool.isClient())
						pool.checkConnections();
				}
			}catch(Exception e){
				logger.error("",e);
			}finally{
				rlock.unlock();
			}
			Thread.sleep(baseCheckInterval);
		}catch(InterruptedException e){
			logger.error("",e);
			break;
		}
		
	}
	public void notifyConnectionReduce(String socketIdentify, String targetServer) {
		ConnectionPool list=getProcesserList(targetServer);
		if (list!=null)
			list.remove(socketIdentify);
	}

}
