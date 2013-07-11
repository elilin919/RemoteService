package com.ling.remoteservice.msg;

import java.net.DatagramPacket;

import com.ling.remoteservice.Configure;
import com.ling.remoteservice.host.ClientIdentify;
import com.ling.remoteservice.host.ServerIdentify;

public interface DatapackHandel {
	public void setMessager(Messager msger);
	public void initSocket(Configure params);
	public DataPack makeSendData(ServerIdentify target,ClientIdentify source,int status, Object[] data);
	public boolean addSendData(DataPack sdata);
	public void addRecvData(DatagramPacket dp);
	public ClientIdentify getLocalIdentify();
	public boolean close();
	public String getLocalHostName();
}
