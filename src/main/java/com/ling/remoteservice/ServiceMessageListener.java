package com.ling.remoteservice;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.sf.cglib.asm.Type;
import net.sf.cglib.core.Signature;
import net.sf.cglib.proxy.MethodProxy;

import org.apache.commons.collections.map.LRUMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.host.ClientIdentify;
import com.ling.remoteservice.host.ServerIdentify;
import com.ling.remoteservice.msg.DataPack;
import com.ling.remoteservice.msg.MessageListener;
import com.ling.remoteservice.msg.Messager;
import com.ling.remoteservice.msg.MessagerFactory;

public class ServiceMessageListener implements MessageListener{
    Class<?> clazz;
    static Log logger = LogFactory.getLog(ServiceMessageListener.class);;
    Object proxyObj;
    Messager messager;
    Manager  manager;
    Lock rlock;
    Lock wlock;
    Map<String,DataPack> dpcache;
    //Object implObj;
    //NetFlowListener netWatcher;
    
    public ServiceMessageListener(Class<?> serverClass){
        init(serverClass);
        this.proxyObj=manager.createInstance(serverClass);
    }
    
    public ServiceMessageListener(Class<?> serverClass,Object implementObject){
    	init(serverClass);
        this.proxyObj=manager.createInstance(serverClass, implementObject);
    }
    
	private void init(Class<?> serverClass) {
		dpcache=new LRUMap(200);
        ReentrantReadWriteLock rwlock=new ReentrantReadWriteLock();
        rlock=rwlock.readLock();
        wlock=rwlock.writeLock();
        
        messager=MessagerFactory.getInstance();
        manager=Manager.getInstance();
        clazz=serverClass;
	}
    @Override
    public void close() {
        logger.error("close:"+clazz.getName());
        Signature sig=new Signature("close","()V");
        MethodProxy mproxy=MethodProxy.find(proxyObj.getClass(),sig);
        
        if (mproxy!=null){
            try {
            	int idx=mproxy.getSuperIndex();
                mproxy.invoke(proxyObj, new Object[0]);
            } catch (Throwable e) {
                logger.error("",e);
            }
        }else{
        	logger.error("can't find close in "+clazz.getName());
        }
    }
    
    @Override
    public String getListenChannel() {
        return clazz.getName();
    }
    
    @Override
    public void process(DataPack datapack, Object[] params) {
        String clazzname=(String)params[0];
        String ident = (String) params[1];
        String sname = (String) params[2];
        String sdesc = (String) params[3];
        if ("close".equalsIgnoreCase(sname) ){
        	logger.error("call close from client:"+datapack.getSourceIdentify());
        	return;
        }
        //netWatcher.logRecvChannel(datapack.getSourceIdentify().toString(), clazzname);
        rlock.lock();
        DataPack resultdp=dpcache.get(datapack.getKey()+"");
        rlock.unlock();
        
        if (resultdp!=null){
            //重新发送内容
            logger.error("Data sended before.resend:"+resultdp.getKey());
            //netWatcher.logSendChannel(resultdp.getTargetIdentify().toString(), clazzname);
            messager.addSendData(resultdp);
            
            return;
        }

        Object[] paramObjs = new Object[params.length - 4];
        System.arraycopy(params, 4, paramObjs, 0, paramObjs.length);
        Signature sig=new Signature(sname,sdesc);
        MethodProxy mproxy=MethodProxy.find(proxyObj.getClass(),sig);
        
        try {
        	Thread.currentThread().setContextClassLoader(clazz.getClassLoader());
        	int idx=mproxy.getSuperIndex();
            Object obj=mproxy.invoke(proxyObj, paramObjs);
            if (mproxy.getSignature().getReturnType()!=Type.VOID_TYPE){
                DataPack result=makeSendData(datapack,clazzname,ident,sname,sdesc,obj);
                wlock.lock();
                dpcache.put(result.getKey()+"", result);
                wlock.unlock();
                //netWatcher.logSendChannel(result.getTargetIdentify().toString(), clazzname);
                result.setRetryKey(System.currentTimeMillis());
                messager.addSendData(result);
            }
        } catch (Throwable e) {
            logger.error("["+datapack.getKey()+"]"+clazz.getName()+":"+sname+sdesc+":"+proxyObj.getClass().getName(),e);
            //netWatcher.logSendChannel(datapack.getSourceIdentify().toString(), clazzname);
            DataPack dp=makeSendData(datapack,clazzname,ident,sname,sdesc,e);
            dp.setRetryKey(System.currentTimeMillis());
            messager.addSendData(dp);
        }finally{
        	Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        }
    }
    private DataPack makeSendData(DataPack datapack,String clazzname,String iden,String sname,String sdesc, Object resultObj) {
    	if (resultObj instanceof Throwable){
            Throwable t=(Throwable)resultObj;
            resultObj=(new Exception("Execute fault in server with Exception :"+t.getMessage()));
		}
		Object[] responseData=new Object[]{clazzname,iden,sname,sdesc,resultObj};
		ServerIdentify target=new ServerIdentify(datapack.getSourceIdentify().toString());
		ClientIdentify source=messager.getLocalIdentify(target);
		DataPack dp=messager.makeSendData(target, source,DataPack.STATUS_RESPONSE, responseData);
        dp.setKey(datapack.getKey());
        dp.setVersion(DataPack.VERSION_Serializable);
        logger.debug("result["+subResult(resultObj)+"]\n["+sname+","+sdesc+"]\n"+"\ndpkey:"+dp.getKey());
		return dp;	
    }
    
    private String subResult(Object rest) {
        if (rest==null ) return null;
        String reststr=rest.toString();
        int leng=reststr.length();
        if (leng<100)
            return reststr;
        return reststr.substring(0,100)+"...(length:"+leng+")";
    }
    public Class<?> getClazz() {
        return clazz;
    }
}
