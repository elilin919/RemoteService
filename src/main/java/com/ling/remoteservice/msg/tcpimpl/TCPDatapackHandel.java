package com.ling.remoteservice.msg.tcpimpl;

import java.net.DatagramPacket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.Configure;
import com.ling.remoteservice.host.ClientIdentify;
import com.ling.remoteservice.host.Identify;
import com.ling.remoteservice.host.ServerIdentify;
import com.ling.remoteservice.msg.DataPack;
import com.ling.remoteservice.msg.DatapackHandel;
import com.ling.remoteservice.msg.Messager;

public class TCPDatapackHandel implements DatapackHandel{
	//BlockingQueue<DataPack> sendQueue;
	Log logger;
	SocketManager socketManager;
	String serverHost;
	//boolean isServer;
	int serverPort,recvBufferSize,recvSizeTimes,sendSizeTimes,datagramRecvBuffer;
	Messager messager;
	public TCPDatapackHandel(){
		logger=LogFactory.getLog(getClass());
		
	}
	@Override
	public void initSocket(Configure params) {
        recvBufferSize=params.getInt("local.tcp-server.recvQueueSize",1000);
        recvSizeTimes=params.getInt("local.tcp-server.socket.receiveBufferSize.PACKLENGTH.TIMES",10);
        sendSizeTimes=params.getInt("local.tcp-server.socket.sendBufferSize.PACKLENGTH.TIMES",10);
		
		socketManager=new SocketManager(this,params);
		serverHost = params.getString("local.tcp-server.host");
		//isServer="true".equals(params.get("local.tcp-server"));
        if ( serverHost == null){
            serverHost = "localhost";
        }
        
        serverPort=socketManager.initServer(serverHost, params.getInt("local.tcp-server.port",9999));
	}
	
	@Override
	public boolean addSendData(DataPack sdata) {
		return socketManager.addSendData(sdata);
		
	}
	public void addRecvData(DatagramPacket dp){
		messager.addRecvData(dp);
	}
	@Override
	public boolean close() {
		//socketManager.closeServer();
		socketManager.close();
		return true;
	}

	@Override
	public ClientIdentify getLocalIdentify() {
		ClientIdentify ident=new ClientIdentify(Identify.PROTOCOL_TCP,null,null,serverHost,serverPort+"");
		logger.debug("return identify:"+ident);
		return ident;
	}

	@Override
	public String getLocalHostName() {
		return serverHost;
	}

	@Override
	public DataPack makeSendData(ServerIdentify target, ClientIdentify source, int status, Object[] data) {
		//InetSocketAddress addr=new InetSocketAddress(target.getHost(),target.getPort());
		DataPack dp=new DataPack(target,source,status,data);
		//dp.setTargetIdentify(target.toString());
		//dp.setRecvPort(source.getPort());
		//dp.setRecvPorts(source.getPortsString());
		return dp;
	}

	@Override
	public void setMessager(Messager msger) {
		this.messager=msger;
	}
	
}
