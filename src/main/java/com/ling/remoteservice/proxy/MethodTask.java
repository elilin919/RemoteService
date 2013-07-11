package com.ling.remoteservice.proxy;

import java.lang.reflect.Method;

import net.sf.cglib.core.Signature;
import net.sf.cglib.proxy.MethodProxy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.msg.DataPack;

public class MethodTask<T> {
    String serviceName;
    Class<?> clazz;
    Method method;
    Object[] args;
    T result;
    DataPack data;
    boolean isResultSet=false;
    Signature signature;
    long time;
    long taskKey;
    private int version=DataPack.VERSION_Serializable;
    private MethodProxy proxy;
    private Object implObj;
    static Log logger=LogFactory.getLog(MethodTask.class);
    
    public MethodTask(Signature signature, String serviceName, Class<?> clazz, Method method, Object[] args, T result) {
        time=System.currentTimeMillis();
        this.signature=signature;
        this.serviceName=serviceName;
        this.clazz=clazz;
        this.method=method;
        this.args=args;
        this.result=result;
        //initDataPack();
    }
    public  Object[] getData(){
    	Object[] params=new Object[4+args.length];
		params[0]=clazz.getName();
		params[1]=serviceName;
		params[2]=signature.getName();
		params[3]=signature.getDescriptor();
		for (int i=4;i<params.length;i++){
            params[i]=args[i-4];
        }
        return params;
        //return res;
    }
    
    
    
    public String getServiceName() {
        return serviceName;
    }
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    public Class<?> getClazz() {
        return clazz;
    }
    public void setClazz(Class<?> clazz) {
        this.clazz = clazz;
    }
    public Method getMethod() {
        return method;
    }
    public void setMethod(Method method) {
        this.method = method;
    }
    public Object[] getArgs() {
        return args;
    }
    public void setArgs(Object[] args) {
        this.args = args;
    }
    public T getResult() {
        try{
            synchronized (this) {
                if (!isResultSet)
                    this.wait();
            }
            return result;
        }catch(Exception e){
            return null;
        }
    }
    public T getResult(long msec) {
        try{
            synchronized (this) {
                if (!isResultSet)
                    this.wait(msec);
            }
            return result;
        }catch(Exception e){
            return null;
        }
    }
    public void setResult(T result) {
        try{
            synchronized (this) {
                isResultSet=true;
                this.result=result;
                this.notifyAll();
            }
        }catch(Exception e){
        }
    }
    public boolean isResultSet() {
        return isResultSet;
    }
    public Signature getSignature() {
        return signature;
    }
    public void setSignature(Signature signature) {
        this.signature = signature;
    }
    public long getTime() {
        return time;
    }
    public void setTaskKey(long key) {
        this.taskKey=key;
    }
    public long getTaskKey() {
        return taskKey;
    }
    public void setMethodProxy(MethodProxy proxy) {
        this.proxy=proxy;
        
    }
    public void setExecuteObject(Object implObj) {
        this.implObj=implObj;
    }
    public MethodProxy getProxy() {
        return proxy;
    }
    public void setProxy(MethodProxy proxy) {
        this.proxy = proxy;
    }
    public Object getImplObj() {
        return implObj;
    }
    public void setImplObj(Object implObj) {
        this.implObj = implObj;
    }
	public int getVersion() {
		return version;
	}
    
}
