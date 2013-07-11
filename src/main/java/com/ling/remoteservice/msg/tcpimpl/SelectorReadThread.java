package com.ling.remoteservice.msg.tcpimpl;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class SelectorReadThread extends Thread {
	Log logger;
	boolean stop = false;
	SocketManager socketManager;
	Selector selector;
	Map<String,ISocketProcesser> schannels;
	ReentrantLock lock;
	//BlockingQueue<SelectionKey> keyBufferQueue;
	public SelectorReadThread(SocketManager socketManager, Selector selector) {
		logger = LogFactory.getLog(getClass());
		// this.processUnion = processUnion;
		this.socketManager = socketManager;
		this.selector = selector;
		//keyBufferQueue=new ArrayBlockingQueue<SelectionKey>(300);
		lock = new ReentrantLock();
		schannels = new HashMap<String,ISocketProcesser>();
		//new DataProcessThread(keyBufferQueue).start();
	}

	public void setStop() {
		stop = true;
		selector.wakeup();
	}
	
	public void run() {
		SelectionKey currentProcessKey=null;
		
		while (!stop) {
			try {
				
				if (schannels.size() > 0) {
					try {
						lock.lock();
						for (ISocketProcesser s : schannels.values()) {
							try {
								s.getSocketChannel().register(selector, SelectionKey.OP_READ, s);
								logger.info("socketchannel regist:" + s.getSocketChannel());
							} catch (ClosedChannelException e) {
								logger.error("", e);
							}
						}
						schannels.clear();
					} finally {
						lock.unlock();
					}
				}
				int events = selector.select();
				if (events > 0) {
					Set<SelectionKey> keys = selector.selectedKeys();
					logger.info("keys size:" + keys.size());
					Iterator<SelectionKey> iter = keys.iterator();
					while (iter.hasNext()) {
						currentProcessKey = iter.next();
						iter.remove();

						try {
							if (currentProcessKey.isReadable()) {
								ISocketProcesser socketProcesser = (ISocketProcesser)currentProcessKey.attachment();
								socketProcesser.readData(currentProcessKey);
							} else {
								int rops = currentProcessKey.readyOps();
								logger.error("read ops:" + rops);
							}
						}
						catch(IOException e){
							logger.error("",e);
							cancelKeyAndCloseChannel(currentProcessKey);
						}
						catch (CancelledKeyException e) {
							logger.error("", e);
							// break;
						}
					}
				} else {
					logger.info("read events:" + events);
					//checkKeys();
				}
			} catch (IOException e) {
				ISocketProcesser socketProcesser = (ISocketProcesser)currentProcessKey.attachment();
				logger.error("["+socketProcesser+"] read handler selector encounter ioexception:"+e.getClass().getName(), e);
				//removeChannel(socketProcesser);
				//break;
			} catch (ClosedSelectorException e) {
				logger.error("selector has been closed", e);
				break;
			}
		}
	}

	private void checkKeys() {
		try {
			Set<SelectionKey> triedKeys = new HashSet<SelectionKey>();
			for (SelectionKey sk : selector.keys()) {
				int ops = sk.interestOps();
				if (ops == 0) {
					triedKeys.add(sk);
					sk.interestOps(SelectionKey.OP_WRITE);
				}
			}
			selector.selectNow();
			Set<SelectionKey> selected = selector.selectedKeys();
			for (SelectionKey sk : selected) {
				if (sk.isWritable()) {
					triedKeys.remove(sk);
				}
				sk.interestOps(0);
			}
			if (!triedKeys.isEmpty()) {
				logger.error("Some keys did not get writable,trying to close them");
				for (SelectionKey sk : triedKeys) {
					cancelKeyAndCloseChannel(sk);
				}
				selector.selectNow();
			}
			logger.error("Spin evasion complete, hopefully system is ok again.");
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	private void cancelKeyAndCloseChannel(SelectionKey sk) {
		try {
			if (sk.channel()!=null)
				sk.channel().close();
			ISocketProcesser socketProcesser = (ISocketProcesser) sk.attachment();
			if (socketProcesser!=null)
				socketManager.notifyConnectionReduce(socketProcesser.getSocketIdentify(), socketProcesser.getTargetServer());
			
			sk.cancel();
			//sk.cancel();
		} catch (IOException e) {
			logger.error("",e);
		}
	}

	public void registChannel(ISocketProcesser socketProcesser) {
		try {
			lock.lock();
			schannels.put(socketProcesser.getSocketIdentify(), socketProcesser);
			logger.error("add socketProcess ["+socketProcesser.getSocketIdentify()+"]"+socketProcesser);
			selector.wakeup();
		} finally {
			lock.unlock();
		}
	}
	
}
