package com.ling.remoteservice.msg;

public interface MessageListener {
	/**
	 * interesting channel.
	 * @return
	 */
	public String getListenChannel();
	
	public void process(DataPack datapack,Object[] params);
	
	public void close();
}
