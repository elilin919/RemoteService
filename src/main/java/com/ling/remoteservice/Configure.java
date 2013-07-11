package com.ling.remoteservice;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class Configure {
	private  Properties config;
	private static Log logger=LogFactory.getLog(Configure.class);
	String configFileName=null;
	long config_file_modify_time=0;
	File configFile=null;
	
	public Configure(String configFileName){
		this.configFileName=configFileName;
		initConfigFile();
		loadConfig();
	}

	public Configure(String configFileName,final long reload_period){
		this(configFileName);
		new Thread(){
			@Override
			public void run() {
				while (true)
				try{
					Thread.sleep(reload_period);
					long file_modify_time=configFile.lastModified();
					if (file_modify_time!=config_file_modify_time){
						logger.error("Reload config "+configFile.getAbsolutePath());
						loadConfig();
					}
				}catch(InterruptedException e){
					logger.error("Reload thread was interrupted.",e);
					break;
				}catch(Exception e){
					logger.error("",e);
					if (configFile==null){
						break;
					}
				}
			}
		}.start();
	}
	
	private void initConfigFile() {
		URL configurl=Thread.currentThread().getContextClassLoader().getResource(configFileName);
		if (configurl==null){
			logger.error("Config file url for ["+configFileName+"] is null. Try create file.");
			String path=getClasspathDir();
			if (path==null){
				URL url = getClass().getClassLoader().getResource("");
				path = url.getPath();
			}
			String configpath=path+"/"+configFileName;
			File f=new File(configpath);
			if (!f.getParentFile().exists())
				f.getParentFile().mkdirs();
			try {
				f.createNewFile();
				logger.error("Created config file ["+f.getAbsolutePath()+"].");
				configurl=Thread.currentThread().getContextClassLoader().getResource(configFileName);
			} catch (IOException e) {
				logger.error("can not create config file:"+configpath);
				logger.error("",e);
			}
		}
		if (configurl!=null){
			String path=configurl.getPath();
			String fpath=path.startsWith("file://")?path.substring(7):path;
			File f=new File(fpath);
			if (!f.exists()){
				f.getParentFile().mkdirs();
				try {
					f.createNewFile();
					logger.error("Created config file ["+f.getAbsolutePath()+"].");
				} catch (IOException e) {
					logger.error("can not create config file:"+fpath);
				}
			}
			if (f.exists()){
				logger.error("Using config file path for:["+configFileName+"] "+f.getAbsolutePath());
				config_file_modify_time=f.lastModified();
				configFile=f;
			}else{
				
			}
		}
	}
	
	private boolean writeConfig(){
		if (configFile!=null){
			BufferedWriter writer=null;
			try {
				writer=new BufferedWriter(new FileWriter(configFile, false),1024);
				config.store(writer, "Write by program:"+new Date());
				writer.flush();
				return true;
			} catch (IOException e) {
				logger.error("",e);
			}finally{
				if (writer!=null)
					try{ writer.close();}catch(Exception e){ logger.error("",e);}
			}
			config_file_modify_time=configFile.lastModified();
		}else{
			logger.error("propertie file is not exist and cannot be create :" + configFileName);
		}
		return false;
	}
	
	public synchronized boolean saveConfig(String[]... keyvalue){
		for (String[] conf: keyvalue){
			config.put(conf[0], conf[1]);
		}
		return writeConfig();
	}
	
	public synchronized boolean saveConfig(String propname,String propvalue){
		config.put(propname, propvalue);
		return writeConfig();
	}
	/**
	 *初始化配置文件
	 */
	public boolean loadConfig() {
		Properties props=new Properties();
		try{
			props.load(new FileInputStream(configFile));
			config=props;
			return true;
		}catch(Exception e){
			logger.error("",e);
		}
		return false;
	}
	public boolean reloadConfig(String confname){
		return loadConfig();
	}
	public String[] getPropertiesByPrefix(String prefix){
		List<String> props=new ArrayList<>();
		for (Entry<Object, Object> ent:config.entrySet()){
			String key=ent.getKey().toString();
			String value=ent.getValue().toString();
			if (key.startsWith(prefix)){
				props.add(value);
			}
		}
		return props.toArray(new String[0]);
	}
	public String[][] getByPrefix(String prefix){
		List<String[]> props=new ArrayList<>();
		for (Entry<Object, Object> ent:config.entrySet()){
			String key=ent.getKey().toString();
			String value=ent.getValue().toString();
			if (key.startsWith(prefix)){
				props.add(new String[]{key,value});
			}
		}
		return props.toArray(new String[0][0]);
	}
	
	public Map<String,String> getMapByPrefix(String prefix){
		Map<String,String> props=new HashMap<>();
		for (Entry<Object, Object> ent:config.entrySet()){
			String key=ent.getKey().toString();
			String value=ent.getValue().toString();
			if (key.startsWith(prefix)){
				props.put(key.substring(prefix.length()),value);
			}
		}
		return props;//.toArray(new String[0][0]);
	}
	
	public String getString(String paramName){
		Object value= config.get(paramName);
		if (value!=null)
			return value.toString();
		return null;
	}
	public String getString(String paramName,String defValue){
		String res=getString(paramName);
		if (res==null) return defValue;
		return res;
	}
	public int getInt(String paramName,int defaultval){
		int val=defaultval;
		Object value=config.get(paramName);
		if (value!=null)
		try{
			val=Integer.parseInt(value.toString().trim());
		}catch(NumberFormatException e){
			logger.error("bad number format for param:"+paramName+" in "+configFileName+" with value:"+value);
		}
		return val;
	}
	public long getLong(String paramName,long defaultval){
		long val=defaultval;
		Object value=config.get(paramName);
		
		if (value!=null)
		try{
			val=Long.parseLong(value.toString().trim());
		}catch(NumberFormatException e){
			logger.error("bad number format for param:"+paramName+" in "+configFileName+" with value:"+value);
		}
		return val;
	}
	public File getFile(String paramName){
		Object value=config.get(paramName);
		if (value!=null){
			String valstr=value.toString().trim();
			return new File(valstr);
		}
		logger.error("There is not any value set for param:"+paramName+" in <"+configFileName+"> "+config);
		return null;
	}
	
	String getClasspathDir(){
		ClassLoader loader=Thread.currentThread().getContextClassLoader();
		if (loader instanceof URLClassLoader)
		for (URL url :((URLClassLoader) loader).getURLs()) {
			File f=new File(url.getPath());
			if (f.isDirectory()){
				return f.getAbsolutePath();
			}
		}
		return null;
	}
	
	public static void main(String[] args) throws InterruptedException{
		final Configure conf=new Configure("test.properties",10000);
		//conf.saveConfig("hello", "world!");
		new Thread(){
			public void run(){
				try {
					sleep(16000);
					conf.saveConfig("hello", "CHANGE the world 2!");
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}.start();
		while (true){
			System.out.println(conf.getString("hello"));
			System.out.println(conf.getInt("initparameter", 0));
			Thread.sleep(1000);
		}
	}

}
