package com.ling.remoteservice.host;

import java.io.Serializable;
import java.util.Random;

public class ServerData implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1032334904742766250L;
	static Random rd=new Random();
	private String protocol;
	private String serverKey;
	private String host;
	private String loginName;
	private String loginPasswd;
	private String portStr;
	private int[] ports;
	

	public ServerData() {
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getServerKey() {
		return serverKey;
	}

	public void setServerKey(String serverKey) {
		this.serverKey = serverKey;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getLoginName() {
		return loginName;
	}

	public void setLoginName(String loginName) {
		this.loginName = loginName;
	}

	public String getLoginPasswd() {
		return loginPasswd;
	}

	public void setLoginPasswd(String loginPasswd) {
		this.loginPasswd = loginPasswd;
	}

	public String getPortStr() {
		return portStr;
	}

	public void setPortStr(String portStr) {
		this.portStr = portStr;
	}
	
	public int getPort(){
		if (getPorts().length==1)
			return getPorts()[0];
	    int idx=Math.abs(rd.nextInt())%getPorts().length;
	    return getPorts()[idx];
	}
	
	public int[] getPorts() {
		return ports;
	}

	public void setPorts(int[] ports) {
		this.ports = ports;
	}
	
	public String toString(){
		return getProtocol()+"://"+getHost()+":"+getPortStr();
	}
}