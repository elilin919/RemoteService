package com.ling.remoteservice.proxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import net.sf.cglib.asm.Type;
import net.sf.cglib.core.Signature;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.Manager;
import com.ling.remoteservice.annonation.Cachable;
import com.ling.remoteservice.annonation.CacheUpdate;
import com.ling.remoteservice.annonation.Local;
import com.ling.remoteservice.annonation.Remote;
import com.ling.remoteservice.cache.CacheEntry;
import com.ling.remoteservice.cache.CacheManagerImpl;
import com.ling.remoteservice.cache.ICacheManager;
import com.ling.remoteservice.msg.Messager;
import com.ling.remoteservice.msg.MessagerFactory;
import com.ling.remoteservice.utils.TypeUtils;

public class LocalCacheInterceptor implements MethodInterceptor {
    static Log logger=LogFactory.getLog(LocalCacheInterceptor.class);

    String interfacename;

    Object implObj;

    boolean remoteModel=false;
    boolean useCache=true;
    //String watchClass="com.sohu.wap.resource.stub";
    Class<?> iclazz;
    Map<String,Integer> limitRecode=new HashMap<String, Integer>();
    ReentrantLock       limitRecodeLock=new ReentrantLock();
    AtomicInteger count=new AtomicInteger(0);
    int executeLimit=60;
    String serviceName=null;
    Messager messager = MessagerFactory.getInstance();
    
    public LocalCacheInterceptor( Class<?> clazz,String serviceName,long timeout,int retry) throws Exception {
    	
        this.iclazz=clazz;
        this.interfacename = clazz.getName();

        if (messager.getServer(serviceName)!=null){
        	logger.error("make remote interceptor for:"+clazz.getName()+"@"+serviceName);
        	implObj = createRemoteImpl(clazz,serviceName,timeout,retry,20);
        	executeLimit=-1;
        }else{
        	logger.error("sever config for "+serviceName+" is null.try make local Interceptor.");
        	implObj=makeLocalInstance(clazz);
        }
        //createCaches(clazz);
    }
    public LocalCacheInterceptor( Class<?> clazz,boolean forceRemote) throws Exception {
        this.iclazz=clazz;
        this.interfacename = clazz.getName();
        final Remote remoteService=clazz.getAnnotation(Remote.class);
        serviceName=remoteService.serviceName();
        long timeout=remoteService.timeout();
        int retry   =remoteService.retry();
        int limitCount   =remoteService.connectionLimit();
        if (messager.getServer(serviceName)!=null){
        	logger.error("make remote interceptor for:"+clazz.getName()+"@"+serviceName);
        	implObj = createRemoteImpl(clazz,serviceName,timeout,retry,limitCount);
        	executeLimit=-1;
        }else{
        	logger.error("Server config for "+serviceName+" is null, Cannot connect to remote service.");
        	//implObj=makeLocalInstance(clazz);
        	throw new RuntimeException();
        }
        //createCaches(clazz);
    }
    public LocalCacheInterceptor( Class<?> clazz) throws Exception {
        this(clazz,null);
    }
    public LocalCacheInterceptor(Class<?> clazz,boolean useCache, Object... initParams) throws Exception {
        //FIXME ..
        this.useCache=useCache;
        this.iclazz=clazz;
        this.interfacename = clazz.getName();
        implObj=makeLocalInstance(clazz,initParams);
        //createCaches(clazz);
    }
    public LocalCacheInterceptor( Class<?> clazz, Object implInstance) throws Exception {
        this.iclazz=clazz;
        this.interfacename = clazz.getName();
        implObj = implInstance;
        if (implObj==null){
            implObj=makeLocalInstance(clazz);
        }
        //createCaches(clazz);
    }

	private Object makeLocalInstance(final Class<?> clazz,final Object... initParams) {
	    //synchronized (clazz) {
		final Local localImp = clazz.getAnnotation(Local.class);
		final Remote remoteService=clazz.getAnnotation(Remote.class);
		//count.set(localImp.executeLimit());
		executeLimit=localImp.executeLimit();
		FutureTask<Object> ftask=new FutureTask<Object>(new Callable<Object>(){
            @Override
            public Object call() throws Exception {
            	Object implInstance=instance(clazz,localImp,remoteService,initParams);
            	implObj=implInstance;
                return implInstance;
            }

		});
		Thread t=new Thread(ftask);//ftask.run();
		t.start();
		Object obj=null;
        try {
            obj = ftask.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Time out for creating instance for class:"+clazz.getName()
                    +" service:"+(remoteService==null?null:remoteService.serviceName())
                    +" localimpl:"+(localImp==null?null:localImp.implementClass()),e);
        }
		return obj;
	    //}
	}

    @SuppressWarnings("unchecked")
    private <T extends Object> T  instance(Class<T> clazz, Local localImp, Remote remoteService,Object... initParams) throws Exception {
        if (localImp!=null){
            try {
                Class<T> c=(Class<T>) clazz.getClassLoader().loadClass(localImp.implementClass());
                String instanceMethod=localImp.instanceMethod();
                if (instanceMethod==null||instanceMethod.length()==0){
                	logger.error("instance implements class by class.newInstance :"+c.getName());
                	if (initParams!=null && initParams.length>0){
                	    Constructor<T> tcont=c.getConstructor(getParameterTypes(initParams));
                	    return tcont.newInstance(initParams);
                	}else
                	    return c.newInstance();
                }else{
                    Method m=c.getDeclaredMethod(instanceMethod,null);
                    logger.error("instance implements class by invoke method:"+c.getName()+":method:"+m.getName());
                    return (T) m.invoke(null, null);
                }
            } catch (ClassNotFoundException e) {
                logger.error("Class Not Found,Try RemoteClass :"+localImp.implementClass());
            } catch (Exception e) {
                logger.error(""+localImp.implementClass()+"/"+localImp.instanceMethod(),e);
                throw e;
            }
        }
        if (remoteService!=null){
        	logger.error("create remote interceptor for:"+clazz.getName()+":"+remoteService.serviceName());
            return createRemoteImpl(clazz,remoteService.serviceName(),remoteService.timeout(),remoteService.retry(),remoteService.connectionLimit());
        }
        logger.error("can't create interceptor or instance for :"+clazz.getName()+"@remoteService:"+(remoteService==null?null:remoteService.serviceName()),new Exception("Error when create implements Object!"));

        return null;
    }
    
    private Class<?>[] getParameterTypes(Object[] initParams) {
        Class<?>[] cts=new Class<?>[initParams.length];
        for (int i=0;i<initParams.length;i++){
            cts[i]=initParams[i].getClass();
        }
        return cts;
    }
    private <T extends Object> T createRemoteImpl(Class<T> clazz, String sname,long timeout,int retry,int limit) {
        Enhancer enc=new Enhancer();
        enc.setSuperclass(clazz);
        enc.setCallback(new RemoteInterceptor(sname,timeout,retry,limit, clazz.getClassLoader(), clazz));
        remoteModel=true;
        return (T)enc.create();
    }

    public Object intercept(Object obj, Method imethod, Object[] args, MethodProxy proxy) throws Throwable {
    	proxy.getSuperIndex();
        long start=System.currentTimeMillis();
        Cachable cab=null;
        CacheUpdate cud=null;
        Signature sig=null;
        Boolean isReachLimit=false;
        try{
            Method method=imethod;

            method = getMethod(method,imethod, args);
            sig=new Signature(method.getName(),Type.getReturnType(method),Type.getArgumentTypes(method));

            cab=method.getAnnotation(Cachable.class);
            cud=method.getAnnotation(CacheUpdate.class);
            if(remoteModel)
            	isReachLimit=checkReachLimit(cab, sig);
            if (isReachLimit!=null && isReachLimit){
                logger.error("reach limit:"+cab.limitBlockSize());
                return null;
            }
            if (imethod.getName().equals("toString") && (imethod.getParameterTypes()==null || imethod.getParameterTypes().length==0)){
                return this.toString();
            }
            if ("close".equals(imethod.getName()) )
            	logger.error("exec method:"+sig,new Exception());
            return executeMethod(sig,proxy, method, cab, cud,args);

        }catch(Exception e){
            logger.error("",e);
            throw e;
        }finally{
            if (isReachLimit!=null && !isReachLimit){
                reduceLimit(cab, sig);
            }
            long cost=System.currentTimeMillis()-start;
            if (!remoteModel && cost>1000)
                logger.error("cost["+cost+"] for execute local method:"+imethod+" args:"+printArgs(args));
        }
    }
    private void reduceLimit(Cachable cab, Signature sig) {
        if (cab!=null && cab.limitBlockSize()!=-1 && sig!=null){
            limitRecodeLock.lock();
            try{
                Integer limit=limitRecode.get(sig.toString());
                if (limit==null) limit=1;
                limit--;
                limitRecode.put(sig.toString(),limit);
                logger.debug("reduce limit:"+limit);
            }finally{
                limitRecodeLock.unlock();
            }
        }
    }
    private Boolean checkReachLimit(Cachable cab, Signature sig) {
        if (cab!=null && cab.limitBlockSize()!=-1 && sig!=null){
            limitRecodeLock.lock();
            try{
                Integer limit=limitRecode.get(sig.toString());
                logger.debug("current block size ["+limit+"] for "+sig+"");
                if (limit==null) limit=0;
                if (limit>= cab.limitBlockSize())
                    return Boolean.TRUE;
                limit++;
                limitRecode.put(sig.toString(),limit);
                return Boolean.FALSE;
            }finally{
                limitRecodeLock.unlock();
            }
        }
        return null;
    }
    private Object executeMethod(Signature sig,MethodProxy proxy, Method method, Cachable cab, CacheUpdate cud, Object[] args) throws Throwable {

        Object ret = null;
        if (cud!=null) {
            ret = invokeMethod(proxy, args);
            CacheManagerImpl.getInstance().updateCache(serviceName, cud, sig, args, remoteModel);
        }else{
	        if (cab!=null && useCache) {
	    		CacheEntry<?> ce = CacheManagerImpl.getInstance().getFromCache(cab, sig, method, args);
	    		Object cachedResult=false;
	    		if (ce!=null){
	    			//have been cached before.
	    			cachedResult = ce.getContent();
	    			if (cachedResult==null){
	    				if (cab.cacheNull()) {
	    					return null; //null cached can be use.
	    				}
	    			}else{
	    				if (!ce.isExpired()) return cachedResult; //got normal result from cache.
	    			}
	    		}
	    		//not in cached before.
	    		ret = invokeMethod(proxy, args);
	    		if (ret!=null){
	    			CacheManagerImpl.getInstance().putIntoCache(cab, sig, method, args, ret);
	    		}else{
	    			if (cab.cacheNull()) CacheManagerImpl.getInstance().putIntoCache(cab, sig, method, args, ret);
	    			if (cab.useExpiredCache() && cachedResult!=null) ret=cachedResult;
	    		}
	    		
	    	} else{
	    		//not any cached annonation present.
	    		ret = invokeMethod(proxy, args);
	    	}
        }
        return ret;
    }
	private Object invokeMethod(MethodProxy proxy, Object[] args) throws Throwable {
		if (executeLimit<0 || count.get()<executeLimit){
			try{
				count.incrementAndGet();
				//cglib methodproxy在线程安全上有缺陷。JDK6下static的载入顺序似乎不太对。
				//proxy没有完全初始化，而是直接用了同一个实例的未完成版本。
				int superIdx=proxy.getSuperIndex();
				Object ret=proxy.invoke(implObj, args);
				return ret;
			}finally{
				count.decrementAndGet();
			}
		}else{
			logger.error("local ["+iclazz.getName()+"] execute reachs execute limit size:"+count.get());
		}
		return null;
	}
    private Method getMethod(Method method,Method imethod, Object[] args) {
        //Method method=null;
        if (imethod.getDeclaringClass()!=iclazz){//并非来自当前代理接口类的请求。
            try{
                method=iclazz.getDeclaredMethod(imethod.getName(), imethod.getParameterTypes());
            }catch(NoSuchMethodException e){
                //logger.info("NotSuchMethod for class ["+iclazz.getName()+"]method["+imethod+"]"+e.getMessage());
                method=findMethod(imethod,iclazz,args);
                //logger.info("find method for ["+iclazz.getName()+"]["+method.getDeclaringClass()+"."+method);
            }
        }
        return method;
    }
    
    private String printArgs(Object[] args) {
        StringBuffer res=new StringBuffer("(");
        for (Object o:args){
            res.append(o).append(",");
        }
        res.append(")");
        return res.toString();
    }
    
    private static Method findMethod(Method imethod, Class<?> iclazz, Object[] args) {
        Method[] methods=iclazz.getDeclaredMethods();
        for (Method m:methods){
            Class<?>[] pts=m.getParameterTypes();
            Class<?>[] ipts =imethod.getParameterTypes();
            if (m.getName().equals(imethod.getName()) && pts.length==ipts.length && TypeUtils.parameterMatch( pts, ipts)){
                logger.info("["+iclazz+"]"+m+" == "+imethod);
                return m;
            }
        }
        return imethod;
    }
    
    public void close(){
        logger.error("close:"+iclazz.getName());
        Signature sig=new Signature("close","()V");
        MethodProxy mproxy=MethodProxy.find(implObj.getClass(),sig);
        
        if (mproxy!=null){
            try {
            	int idx=mproxy.getSuperIndex();
                mproxy.invoke(implObj, new Object[0]);
            } catch (Throwable e) {
                logger.error("",e);
            }
        }else{
        	logger.error("can't find close()V at "+iclazz.getName());
        }
    }

}
