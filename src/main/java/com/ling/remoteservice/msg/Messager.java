package com.ling.remoteservice.msg; 

import java.net.DatagramPacket;
import java.util.List;
import java.util.Map;

import com.ling.remoteservice.host.ClientIdentify;
import com.ling.remoteservice.host.ServerIdentify;

public interface Messager {
	
	public static int LISTENER_TYPE_SERVER=0;
	public static int LISTENER_TYPE_CLIENT=1;
	
	public boolean   addSendData(DataPack dp);
	public DataPack  getMsg();
	public void      sendMsg(ServerIdentify target,ClientIdentify source,int status,Object[] data);
	public void      addRecvData(DatagramPacket dgPack);
	public boolean   isHealth();
	public boolean   close();
	
	public Map<String, ClientIdentify> getAllRemoteHosts(String serviceName);
	public ClientIdentify getLocalIdentify(ServerIdentify target);
	public DataPack makeSendData(ServerIdentify target,ClientIdentify source,int status, Object[] data);
	public void addListener(MessageListener listener, int listenerTypeServer);
	
	public List<String>  getAllServer();
	public ServerIdentify getServer(String servicename);
	
	public void addRegistHost(String servicename, String client);
}
