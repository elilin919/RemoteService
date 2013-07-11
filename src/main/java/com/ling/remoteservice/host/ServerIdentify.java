package com.ling.remoteservice.host;


public class ServerIdentify extends Identify{
	public ServerIdentify(String configstr) {
		super(configstr);
		// TODO Auto-generated constructor stub
	}
	public ServerIdentify(String protocol,String name,String passwd,String host,String recvPorts){
		super(protocol,name,passwd,host,recvPorts);
	}
	public ServerIdentify(String protocol,String name,String passwd,String host,int[] ports){
		super(protocol,name,passwd,host,ports);
	}
	/**
	 * 
	 */
	private static final long serialVersionUID = -7713312212278797163L;
	
}
