package com.ling.remoteservice.msg.tcpimpl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import com.ling.remoteservice.msg.DataPack;

public interface ISocketProcesser {
	public static byte[] datapackStart =new byte[]{'^','!','!','!','!','!','#'};
	public static byte[] datapackEnd   =new byte[]{'$','!','!','!','!','!','#'};
	public static byte[] datapackRegist=new byte[]{'@','!','!','!','!','!','#'};
	public static byte[] datapackHealth=new byte[]{'%','!','!','!','!','!','#'};

	public InetSocketAddress getRemoteAddress();

	public void close();

	public String getSocketIdentify();

	public SocketChannel getSocketChannel();

	public String getTargetServer();
	public void setTargetServer(String targetIdentify);

	public void readData(SelectionKey currentProcessKey) throws IOException;
	
	public void sendData(DataPack sendData) throws IOException;
}
