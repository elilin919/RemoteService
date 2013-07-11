package com.ling.remoteservice.msg;

import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class UncompleteGramBuffer {
    static UncompleteGramBuffer instance = null;

    Log logger = null;

    Map<String,DataPack[]> dataBuffers = null;

    Map<String,Long> bufferTimestamp = null;

    Object bufferlock = new Object();
    Lock rlock,wlock;
    
    public static synchronized UncompleteGramBuffer getInstance() {
        if (instance == null)
            instance = new UncompleteGramBuffer();
        return instance;
    }

    private UncompleteGramBuffer() {
        ReentrantReadWriteLock rwlock=new ReentrantReadWriteLock();
        rlock=rwlock.readLock();
        wlock=rwlock.writeLock();
        logger = LogFactory.getLog(getClass());
        dataBuffers = new HashMap<String,DataPack[]>();
        bufferTimestamp = new HashMap<String,Long>();
        new CleanThread().start();
    }

    // 添加接收到的数据包
    public DataPack addDataGram(DatagramPacket p) {
        //String addr = p.getSocketAddress().toString();

        DataPack rpack = null;

        DataPack dpack = null;
        try {
            dpack = new DataPack(p);
        } catch (Exception e) {
            logger.error("bad data.can't formated datapack!", e);
        }
        if (dpack != null) {
            // 当前数据包完整
            if (dpack.getPtotal() == 1) {
            	logger.debug("received single:["+dpack.getKey()+"]"+dpack.getPserial()+"/"+dpack.getPtotal());
                rpack = dpack;
            }
            // 当前数据包不完整
            else {
                logger.debug("received multi:["+dpack.getKey()+"]"+dpack.getPserial()+"/"+dpack.getPtotal());
                DataPack cdp = addCurrentBuffer(dpack.getKey(), dpack);
                // 当前加入的是最后的数据包
                if (cdp != null) {
                    // 数据正常
                    if (cdp.getBytesData().length == cdp.getDatalength()){
                        rpack = cdp;
//                        if (rpack.getRecvPort()!=0)
//                            rpack.setPort(rpack.getRecvPort());
                    }else
                        logger.error("数据有错误!总长度和数据长度不符.");
                }
            }
        }
        return rpack;
    }

    // 返回当前服务器发送的当前key的未完整数据列
    private DataPack addCurrentBuffer(long datakey, DataPack dpack) {
        String bufferkey=datakey+"";
        rlock.lock();
        DataPack[] cbuffer = dataBuffers.get(bufferkey);
        rlock.unlock();
        if (cbuffer == null) {
            wlock.lock();
            cbuffer=dataBuffers.get(bufferkey);
            if (cbuffer==null){
                cbuffer=new DataPack[dpack.getPtotal()];
                dataBuffers.put(bufferkey, cbuffer);
            }
            wlock.unlock();
        }
        DataPack combinedp = null;
        synchronized (cbuffer) {
            cbuffer[dpack.getPserial()] = dpack;
            // 合并数据
            combinedp = combineData(cbuffer);
            // 删除已经完整的数据
            wlock.lock();
            if (combinedp != null) {
                dataBuffers.remove(bufferkey);
                bufferTimestamp.remove(bufferkey);
            } else{
                bufferTimestamp.put(bufferkey, new Long(System.currentTimeMillis()));
            }
            wlock.unlock();
        }
        return combinedp;
    }

    /**
     * 合并同类数据包
     * 
     * @param dps
     * @return
     */
    private DataPack combineData(DataPack[] dps) {
        for (int i = 0; i < dps.length; i++) {
            if (dps[i] == null){
                //logger.debug("Missing :"+i+"of"+dps.length);
                return null;
            }
        }
        //logger.debug("All "+dps.length+" data received.");
        for (int i = 1; i < dps.length; i++) {
            dps[0].addDataPack(dps[i]);
        }
        return dps[0];
    }

    class CleanThread extends Thread {
        public void run() {
          
            while (true) {
                try{
                    List<String> dlist = new ArrayList<String>();
                    Map<String,Long> copyTimes=new HashMap<String, Long>();
                    rlock.lock();
                        copyTimes.putAll(bufferTimestamp);
                    rlock.unlock();
                    for (Entry<String, Long> ent:copyTimes.entrySet()){
                        String bufferkey=ent.getKey();
                        long   time=ent.getValue();
                        if (time+120000<System.currentTimeMillis())  {//2分钟的数据
                            dlist.add(bufferkey);
                        }
                    }
                    wlock.lock();
                    for (String key:dlist) {
                        logger.debug("remove key:"+key);                        
                        bufferTimestamp.remove(key);
                        dataBuffers.remove(key);
                    }
                    wlock.unlock();
                    sleep(30000);
                }catch (InterruptedException e) {
                    break;
                }catch(Exception e){
                    logger.error("",e);
                }
            }
        }
    }
}
