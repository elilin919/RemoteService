package com.ling.remoteservice.host;

import java.io.Serializable;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Identify implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 3910255589609156987L;
	public static  final String PROTOCOL_UDP="UDP";
	public static  final String PROTOCOL_TCP="TCP";
	
	private String protocol;
	ServerData[] data ;// = new ServerData();
	
	//Pattern modPattern=Pattern.compile("((.*?)://)*(.*?)\\/(.*?)@(.*?)\\:(.*)");
	Pattern modPattern=Pattern.compile("((.*?):\\/\\/)?((.*?)\\/(.*?)@)?(.*?):(.*?)(?:;|$)");
    Log logger=LogFactory.getLog(Identify.class);
	
	public Identify(String confstr){
		String[] confs=confstr.split(";");
		data = new ServerData[confs.length];
		
		for (int i=0;i<confs.length;i++){
			String configstr=confs[i];
			data[i]= new ServerData();
		    data[i].setServerKey(configstr);
		    Matcher m=modPattern.matcher(configstr);
		    while (m.find()){
		    	String protocol=m.group(2);
		    	if (protocol==null){
		    		protocol=PROTOCOL_TCP;
		    	}
		    	data[i].setProtocol(protocol);
		    	if (this.protocol==null) setProtocol(protocol);
		    	
		        data[i].setLoginName(m.group(4)); 
		        data[i].setLoginPasswd(m.group(5));
		        data[i].setHost(m.group(6));
		        data[i].setPortStr(m.group(7));
	//	        logger.info("[PROTOCOL]"+protocol+"\n[loginName]"+loginName+
	//	        		    "\n[loginPasswd]"+loginPasswd+
	//	        		    "\n[host]"+host+
	//	        		    "\n[portStr]"+portStr);
		        data[i].setPorts(parseIntArray(data[i].getPortStr()));
		        
		    }
		}
	}

	private int[] parseIntArray(String portstr) {
        String[] pstr=portstr.split(",");
        int[] ps=new int[pstr.length];
        for (int i=0;i<pstr.length;i++){
            ps[i]=Integer.parseInt(pstr[i]);
        }
        return ps;
    }
	private String toIntArray(int[] ports) {
        StringBuffer res=new StringBuffer();
        for (int p:ports){
            if (res.length()>0)
                res.append(",");
            res.append(p);
        }
        return res.toString();
    }	
	public Identify(String protocol,String loginName,String loginPasswd,String host,String portStr){
		this.data=new ServerData[1];
		this.data[0]=new ServerData();
		this.data[0].setProtocol(protocol);
		this.data[0].setLoginName(loginName);
		this.data[0].setLoginPasswd(loginPasswd);
		this.data[0].setHost(host);
		this.data[0].setPorts(parseIntArray(portStr));
		this.data[0].setPortStr(portStr);
		this.data[0].setServerKey(toString());
	}
    public Identify(String protocol,String loginName,String loginPasswd,String host,int[] port){
    	this.data=new ServerData[1];
    	this.data[0]=new ServerData();
    	this.data[0].setProtocol(protocol);
		this.data[0].setLoginName(loginName);
		this.data[0].setLoginPasswd(loginPasswd);
		this.data[0].setHost(host);
		this.data[0].setPorts(port);
		this.data[0].setPortStr(toIntArray(data[0].getPorts()));
		this.data[0].setServerKey(toString());
	}
	public String toString(){
		StringBuilder builder=new StringBuilder();
		for (ServerData d:data){
			if (builder.length()>0) builder.append(";");
			builder.append(d.getProtocol()).append("://").append(d.getHost()).append(":").append(d.getPortStr());
		}
		 return builder.toString();
	}

	public ServerData[] getServers(){
		return data;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}
	
}
