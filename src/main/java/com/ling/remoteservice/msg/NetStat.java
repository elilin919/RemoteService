package com.ling.remoteservice.msg;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class NetStat {
	String host;
	long startTime;
	long total;
	//long cacheDataPackCount;
	long totalDataPackCount;
	ConcurrentHashMap<String, AtomicLong> chanelCount;
	//ConcurrentHashMap<String, AtomicLong> chanelSentCount;
	public NetStat(){
		startTime = System.currentTimeMillis();
		chanelCount=new ConcurrentHashMap<String, AtomicLong>();
		//chanelSentCount=new ConcurrentHashMap<String, AtomicLong>();
	}
	public long getTotalDataPackCount() {
		return totalDataPackCount;
	}

	public void setTotalDataPackCount(long totalDataPackCount) {
		this.totalDataPackCount = totalDataPackCount;
	}


	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public long getTotal() {
		return total;
	}

	public void setTotal(long totalKB) {
		this.total = totalKB;
	}

	public void addTotalbyte(long morebyte) {
		if (Long.MAX_VALUE - total < morebyte) {
			total = 0L;
			startTime = System.currentTimeMillis();
		}
		this.total += morebyte;

	}

	public long flowAverage(long timeUnion) {
		if (timeUnion < 100)
			timeUnion = 100;
		long count = (System.currentTimeMillis() - startTime + timeUnion - 1) / timeUnion;

		return total / count;
	}

	public void addPackCount() {
		totalDataPackCount++;

	}
	
	public void addChannelCount(String channel){
		AtomicLong count=chanelCount.get(channel);
		if (count==null){
			chanelCount.putIfAbsent(channel, new AtomicLong());
		}
		count=chanelCount.get(channel);
		if (count!=null)
			count.incrementAndGet();
	}

	public Map<String,Long> getChannelStatus(){
		Map<String,Long> recv=new HashMap<String, Long>();
		for (java.util.Map.Entry<String,AtomicLong> ent:chanelCount.entrySet()){
			recv.put(ent.getKey(), ent.getValue().get());
		}
		return recv;
	}
}
