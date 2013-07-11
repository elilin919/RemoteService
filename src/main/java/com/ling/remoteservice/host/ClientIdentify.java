package com.ling.remoteservice.host;



public class ClientIdentify extends Identify{

	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3969636521202282203L;
	public ClientIdentify(String configstr){
	    super(configstr);
	}
	public ClientIdentify(String protocol,String name,String passwd,String host,String recvPorts){
		super(protocol,name,passwd,host,recvPorts);
	}
	public ClientIdentify(String protocol,String name,String passwd,String host,int[] ports){
		super(protocol,name,passwd,host,ports);
	}
	
	public String getProtocol() {
		return data[0].getProtocol();
	}


	public String getServerKey() {
		return data[0].getServerKey();
	}


	public String getHost() {
		return data[0].getHost();
	}
	public void setHost(String host){
		data[0].setHost(host);
	}

	public String getLoginName() {
		return data[0].getLoginName();
	}

	public String getLoginPasswd() {
		return data[0].getLoginPasswd();
	}
	public String getPortStr() {
		return data[0].getPortStr();
	}
	
	public int getPort(){
		return data[0].getPort();
	}
	
	public int[] getPorts() {
		return data[0].getPorts();
	}
	
}
