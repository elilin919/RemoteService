package com.ling.remoteservice.proxy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.sf.cglib.core.Signature;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.annonation.Remote;
import com.ling.remoteservice.host.ClientIdentify;
import com.ling.remoteservice.host.ServerIdentify;
import com.ling.remoteservice.msg.DataPack;
import com.ling.remoteservice.msg.MessageListener;
import com.ling.remoteservice.msg.Messager;
import com.ling.remoteservice.msg.MessagerFactory;

public class RemoteInterceptor  extends Thread implements MethodInterceptor,MessageListener{
    String serviceName;
    Class<?> clazz;
    Long timeout;
    Integer retry;
    Lock rlock,weakMapRlock;
    Lock wlock,weakMapWlock;
    Map<String,MethodTask<Object>>  taskList;
    Map<String, MethodTask<Object>> weakTaskList; //过期，但是未被清除的任务存储在该map中。
    long lastCleanTime;
    Log logger;
    int  remoteServiceLimit;
    Random rd=new Random();
    //ConnectionCounter connectLimiter=ConnectionCounter.getInstance();
    AtomicInteger count;
    //NetFlowListener netWatcher;
    Messager messager;
    public RemoteInterceptor( String serviceName,long timeout,int retry,int limit, ClassLoader classLoader, Class<?> clazz) {
    	count=new AtomicInteger(0);
    	messager=MessagerFactory.getInstance();
    	//netWatcher=Manager.getInstance().createInstance(NetFlowListener.class);
        lastCleanTime=System.currentTimeMillis();
        logger=LogFactory.getLog(getClass());
        ReentrantReadWriteLock rwlock=new ReentrantReadWriteLock();
        rlock=rwlock.readLock();
        wlock=rwlock.writeLock();
        ReentrantReadWriteLock mrwlock=new ReentrantReadWriteLock();
        weakMapRlock=mrwlock.readLock();
        weakMapWlock=mrwlock.writeLock();
        //LRUMap lrumap=new LRUMap(500);
        weakTaskList=new WeakHashMap<String, MethodTask<Object>>();
        taskList=new HashMap<String, MethodTask<Object>>();
        this.serviceName=serviceName;
        this.clazz=clazz;
        this.timeout=timeout;
        this.retry=retry;
        this.remoteServiceLimit=limit;
        messager.addListener(this,Messager.LISTENER_TYPE_CLIENT);
        setName(serviceName+":"+clazz.getName());
        start();
    }

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
    	int idx=proxy.getSuperIndex();
    	if (count.get()>=remoteServiceLimit){
    		logger.error("remote service '"+clazz.getName()+"' reachs execute limit size:"+remoteServiceLimit);
    		return null;
    	}
    	String sname=serviceName;
    	ServerIdentify server=messager.getServer(sname);
        if (server==null){
        	logger.error("Bad server config for :"+sname+". Please check the serverlist.properties.", new NullPointerException());
        	return null;
        }
    	try{
    		count.incrementAndGet();
	        long start=System.currentTimeMillis();
	        
	        Long tout=timeout;
	        Integer rtry=retry;
	        Remote r=method.getAnnotation(Remote.class);
	        Signature sig=proxy.getSignature();
	        
	        if (sig.getName().equals("toString") && sig.getDescriptor().equals("()Ljava/lang/String;"))
	            return this.toString();
	        if (sig.getName().equals("close")    && sig.getDescriptor().equals("()V"))
	            return null;
	        if (r!=null){
	            sname=r.serviceName();
	            tout=r.timeout();
	            rtry=r.retry();
	        }
	        
	        
	        MethodTask<Object> mtask=new MethodTask<>(sig,sname,clazz,method,args,null);
	        //logger.debug("try execute remote:["+mtask.getSignature()+"]["+printArgs(mtask.getArgs())+"]:service:"+sname+":"+server.getHost()+":"+server.getPortStr());
	        DataPack dp=makeDatapack(server,mtask);
	        mtask.setTaskKey(dp.getKey());
	        //taskList的lock
	        
	        String taskkey=dp.getKey()+"";
	        wlock.lock();
	        taskList.put(taskkey,mtask);
	        //logger.debug("task has put in map ["+taskkey+"]:"+taskList.get(taskkey));
	        wlock.unlock();
	        
	        weakMapWlock.lock();
	        weakTaskList.put(taskkey,mtask);
	        //logger.debug("task is putting in weakmap ["+taskkey+"]:"+weakTaskList.get(taskkey));
	        weakMapWlock.unlock();
	        
	        logger.debug("execute remote:["+mtask.getSignature()+"]"+"["+server.toString()+"]:dp.key:"+taskkey);
	        Object rest = null;
	        try{
	            rest=execute(mtask, taskkey, dp, tout, rtry);
	        }catch(Exception e){
	            logger.error("",e);
	        }
	        wlock.lock();
	        //logger.debug("task is removing from map ["+taskkey+"]:"+taskList.get(taskkey));
	        taskList.remove(taskkey);
	        wlock.unlock();
	        long cost=(System.currentTimeMillis()-start);
	        if (cost>1000)
	            logger.error("cost["+cost+"] for execute remote method at server["+server+"]key["+dp.getKey()+"]:"+method+" :"+printArgs(args));
	        
	        if (rest!=null && rest instanceof Throwable){
	        	logger.error("Exception when execute remote method at server["+server+"]key["+dp.getKey()+"]:"+method+" :"+printArgs(args));
	            throw (Throwable)rest;
	        }
	        return rest;
    	}finally{
    		int c=count.decrementAndGet();
    		if (c>10 && rd.nextInt()%100==1){
    			logger.error(clazz.getName()+" current block size :"+c);
    		}
    	}
    }
    
//    public void run(){
//        while (true){
//            int count=taskList.size();
//            if (count>10)
//                logger.error("task list size: "+count+" for remote executor:"+getListenChannel());
//            //lastCleanTime=System.currentTimeMillis();
//            try {
//                sleep(30000);
//            } catch (InterruptedException e) {
//                break;
//            }
//        }
////            weakMapWlock.lock();
////            if ((System.currentTimeMillis()-lastCleanTime)>300000){
////                weakMapWlock.unlock();
////                
////                lastCleanTime=System.currentTimeMillis();
////                List<String> outtimelist=getOutDateTask();
////                //wlock.lock();
////                clearOuttimeTask(outtimelist);
////                //wlock.unlock();
////            }else{
////                weakMapWlock.unlock();
////            }
//        
//    }
//    private void clearOuttimeTask(List<String> outtimelist) {
//        if (outtimelist.size()>0){
//            logger.error("try clean outtime task size:"+outtimelist.size());
//            wlock.lock();
//            for (String key:outtimelist){
//                taskList.remove(key);
//            }
//            wlock.unlock();
//        }
//    }
//
//    private List<String> getOutDateTask() {
//        List<String>  keylist=new ArrayList<String>();
//        List<MethodTask<Object>> tlist=new ArrayList<MethodTask<Object>>();
//        tlist.addAll(weakTaskList.values());
//        for (MethodTask<Object> task:tlist){
//            long tasklast=System.currentTimeMillis()-task.getTime();
//            if (tasklast>300000)
//                keylist.add(task.getTaskKey()+"");
//        }
//        return keylist;
//    }

    private Object execute(MethodTask<Object> mtask, String taskkey, DataPack dp, Long tout, Integer rtry) {
        Object rest=null;
        boolean success=execute(dp);
        if (!success)
        	return rest;
        //执行必须在wait之后
        //logger.info(method.getReturnType().getName());
        if (mtask.getMethod().getReturnType()==void.class){
            logger.debug("return immd  void  for dp.key:"+taskkey);
            return null;
        }
        if (tout==0){
            logger.debug("return immd with 0 timeout for dp.key:"+taskkey);
            return null;
        }
        if (tout<0){ 
            rest=mtask.getResult();
            if (!mtask.isResultSet())
                logger.error("return null because of timeout for  dp.key:"+taskkey);
            else
                logger.debug("return result "+subResult(rest)+" for dp.key:"+taskkey);
        }else{
            if (rtry>1){
                rest=mtask.getResult(tout/rtry);
                if (!mtask.isResultSet())
	                for (int i=1;i<rtry;i++){
	                    //dp.setRetryKey(dp.);
	                	//FIXME 是否重新随机分配接收端口？
	                    //dp.setRecvPort(messager.getReceivePort());
	                    boolean success2=execute(dp);
	                    if (!success2){
	                    	return null;
	                    }
	                    rest=mtask.getResult(tout/rtry);
	                    if (!mtask.isResultSet()){
	                        logger.error("timeout of retry ["+i+"]"+(tout/rtry)+" for exec"+mtask.getServiceName()+"(target host:"+dp.getTargetIdentify()+"):"+mtask.getSignature()+" dp.key:"+taskkey,new Exception());
	                    }else{
	                        logger.debug("return result at ["+i+"] retry :"+subResult(rest)+" for dp.key:"+taskkey);
	                        break;
	                    }
	                }
            }else{
                rest=mtask.getResult(tout);
                if (!mtask.isResultSet())
                    logger.error("return null because of timeout for execute "+mtask.getServiceName()+"(target host:"+dp.getTargetIdentify()+"):"+mtask.getSignature()+" dp.key:"+taskkey,new Exception());
                else
                    logger.debug("return result "+subResult(rest)+" for dp.key:"+taskkey);
            }
        }
        return rest;
    }

    private String subResult(Object rest) {
        if (rest==null ) return null;
        String reststr=rest.toString();
        int leng=reststr.length();
        if (leng<100)
            return reststr;
        return reststr.substring(0,100)+"...(length:"+leng+")";
    }

    private boolean execute(DataPack dp) {
    	//netWatcher.logSendChannel(dp.getTargetIdentify().toString(), clazz.getName());
    	dp.setRetryKey(System.currentTimeMillis());
        return messager.addSendData(dp);
    }

    private DataPack makeDatapack(ServerIdentify server,MethodTask<Object> mtask) {
        //int port=server.getPort();
        //InetSocketAddress isaddr = new InetSocketAddress(server.getHost(), port);
        Object[] data=mtask.getData();
        //String sendXml=new String(data);
        //logger.info(sendXml.length()>1000?sendXml.substring(0, 1000):sendXml);
        //Messager msg=messager;
        ClientIdentify source=messager.getLocalIdentify(server);
        DataPack dp=messager.makeSendData(server, source,DataPack.STATUS_REQUEST, data);
        	//new DataPack(isaddr.getAddress(),port,DataPack.STATUS_REQUEST,messager.getReceivePort(),messager.getRecvPortStr(),data);
        dp.setVersion(mtask.getVersion());
        return dp;
    }

    private String printArgs(Object[] args) {
        StringBuffer res=new StringBuffer("(");
        for (Object o:args){
            res.append(o).append(",");
        }
        res.append(")");
        return res.toString();
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String getListenChannel() {
        return clazz.getName();
    }

    @Override
    public void process(DataPack datapack, Object[] params) {
        //process
    	String taskkey=datapack.getKey()+"";
        rlock.lock();
        MethodTask<Object> task=taskList.get(taskkey);
        //logger.debug("task is getting from map ["+taskkey+"]:"+taskList.get(taskkey));
        rlock.unlock();
        if (task==null){
            weakMapRlock.lock();
        	task=weakTaskList.get(taskkey);
        	//logger.debug("task is putting in weakmap ["+taskkey+"]:"+weakTaskList.get(taskkey));
        	weakMapRlock.unlock();
        	if (task==null){
              String cname=(String) params[0];
              String iden=(String) params[1];
              String sname=(String) params[2];
              String sdesc=(String) params[3];
        		logger.error("task is null when get result! "+cname+"."+iden+"."+sname+"."+sdesc+"  datapack key:"+datapack.getKey());
        	}
        }
        //netWatcher.logRecvChannel(datapack.getSourceIdentify().toString(), clazz.getName());
        if (task!=null){
//        	String cname=(String) params[0];
//        	String iden=(String) params[1];
//        	String sname=(String) params[2];
//        	String sdesc=(String) params[3];
        	if (params!=null && params.length==5){
        		task.setResult(params[4]);
        	}else{
        		if (params==null)
        			logger.error("result params is null!",new Exception());
        		else{
        			StringBuffer res=new StringBuffer();
        			for (Object p:params)
        				res.append("params:"+p);
        			logger.error("result params is:"+res,new Exception());
        		}
        	}
        }
    }   

}
