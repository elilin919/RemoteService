package com.ling.remoteservice.msg;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.annonation.Local;


public class NetFlowListener extends Thread {
	static Map<String, NetStat> receivedFlowStat = new ConcurrentHashMap<String, NetStat>(new HashMap<String, NetStat>());
	static Map<String, NetStat> sentFlowStat = new ConcurrentHashMap<String, NetStat>(new HashMap<String, NetStat>());

	static final int type_recv = 0;
	static final int type_send = 1;
	BlockingQueue<Object[]> packageQueue;
	AtomicLong missingSentDatapack=new AtomicLong(0);
	AtomicLong missingRecvDatapack=new AtomicLong(0);
	static Log logger = LogFactory.getLog(NetFlowListener.class);
	
	public NetFlowListener() {
		packageQueue=new ArrayBlockingQueue<Object[]>(200);
		start();
	}

	NetStat getNetStat(String host, int type) {
		
		Map<String, NetStat> map = null;
		if (type == type_recv)
			map = receivedFlowStat;
		if (type == type_send)
			map = sentFlowStat;
		NetStat stat = map.get(host);
		if (stat == null) {
			synchronized (map) {
				stat = map.get(host);
				if (stat == null) {
					stat = new NetStat();
					stat.setHost(host);
					map.put(host, stat);
				}
			}
		}
		return stat;
	}

	
	public Map<String, NetStat> getReceivedNetStat() {
		HashMap<String, NetStat> map = new HashMap<String, NetStat>();
		map.putAll(receivedFlowStat);
		return map;
	}

	
	public Map<String, NetStat> getSentNetStat() {
		HashMap<String, NetStat> map = new HashMap<String, NetStat>();
		map.putAll(sentFlowStat);
		return map;
	}

	public void logSentFlow(DataPack p) {
		if (p==null) return;
		Object[] params=p.getParams();
		String serviceName=(String) params[0];
		boolean added=packageQueue.offer(new Object[]{type_send,p.getTargetIdentify().toString(),p.getBytesData().length,serviceName});
		if (!added)
			missingSentDatapack.incrementAndGet();
	}

	public void logReceivedFlow(DataPack p) {
		if (p==null) return;
		//packageQueue.offer(new Object[]{type,host,});
		Object[] params=p.getParams();
		String serviceName=(String) params[0];
		boolean added=packageQueue.offer(new Object[]{type_recv,p.getSourceIdentify().toString(),p.getBytesData().length,serviceName});
		if (!added)
			missingRecvDatapack.incrementAndGet();
	}
	
	long lastlogtime=System.currentTimeMillis();
	public void run(){
		while(true){
			try {
				Object[] params = packageQueue.take();
				
				Integer type=(Integer) params[0];
				String  host=(String) params[1];
				Integer    blength=(Integer) params[2];
				String  sname=(String) params[3];
				//String host = source.toString();// +":"+p.getPort();
				NetStat stat = getNetStat(host,type);
				if (stat != null) {
					int packlength = blength;
					stat.addTotalbyte(packlength);
					stat.addPackCount();
					
					stat.addChannelCount(sname);
				}
				
				if (System.currentTimeMillis()-lastlogtime > 30000l){
					logger.error(getLogStatus());
				}
				
			} catch (InterruptedException e) {
				logger.error("",e);
				break;
			}catch(Exception e){
				logger.error("",e);
			}
		}
	}

	
	Map<String,Long> recvCountStatus=new HashMap<String, Long>();
	Map<String,Long> sendCountStatus=new HashMap<String, Long>();
	long lastLogTime=0;
	
	
	public String getLogStatus() {
		try{
			long now=System.currentTimeMillis();
			long lastTime=0;
			if (lastLogTime>0){
				lastTime=now-lastLogTime;
			}
            Map<String,NetStat> rnetstats=getReceivedNetStat();
            StringBuffer res=new StringBuffer("\n================="+new Date()+"=====================\n");
            res.append("missing count recv:"+missingRecvDatapack);
            res.append("\nmissing count sent:"+missingSentDatapack).append("\n");
            for (Entry<String, NetStat> ent:rnetstats.entrySet()){
                res.append("received status: ");
                res.append(ent.getKey()).append("--->local   ")
                   .append(ent.getValue().getTotal()/1024).append("(total KB)\n")
                   .append("        [Total DataPack Received]")
                   .append(ent.getValue().getTotalDataPackCount()).append("\n");
                Map<String,Long> channelRecvCounts=ent.getValue().getChannelStatus();
                for (Entry<String, Long> count:channelRecvCounts.entrySet()){
                	res.append("          [").append(count.getValue()).append("]").append(count.getKey());
                	Long lastcount=recvCountStatus.get(count.getKey()+"@"+ent.getKey());
                	if (lastcount!=null && lastTime>0){
                		res.append("     [recv] ").append(count.getValue()-lastcount).append(" in ").append(lastTime/1000).append(" seconds.");
                	}
                	res.append("\n");
                	recvCountStatus.put(count.getKey()+"@"+ent.getKey(),count.getValue());
                }
                
            }
            res.append("-------------------\n");
            Map<String,NetStat> snetstats=getSentNetStat();
            for (Entry<String, NetStat> ent:snetstats.entrySet()){
                res.append("sent status: ");
                res.append("local--->").append(ent.getKey()).append("    ")
                   .append(ent.getValue().getTotal()/1024).append("(total KB)\n")
                   .append("        [Total DataPack Send]")
                   .append(ent.getValue().getTotalDataPackCount()).append("\n");
                Map<String,Long> channelRecvCounts=ent.getValue().getChannelStatus();
                for (Entry<String, Long> count:channelRecvCounts.entrySet()){
                	res.append("          [").append(count.getValue()).append("]").append(count.getKey());
                	Long lastcount=sendCountStatus.get(count.getKey()+"@"+ent.getKey());
                	if (lastcount!=null && lastTime>0){
                		res.append("     [sent] ").append(count.getValue()-lastcount).append(" in ").append(lastTime/1000).append(" seconds.");
                	}
                	res.append("\n");
                	sendCountStatus.put(count.getKey()+"@"+ent.getKey(),count.getValue());
                } 
            }
            
            lastLogTime=now;
            return res.toString();
		}catch(Exception e){
			return e.getMessage();
		}
	}

	
	public void close() {
		this.interrupt();		
	}
}
