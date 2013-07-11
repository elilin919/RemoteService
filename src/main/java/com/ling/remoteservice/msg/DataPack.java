package com.ling.remoteservice.msg;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.host.ClientIdentify;
import com.ling.remoteservice.host.ServerIdentify;
import com.ling.remoteservice.utils.BytesUtils;
import com.ling.remoteservice.utils.SerializableInput;

public class DataPack {
    public static int STATUS_REQUEST=0;
    public static int STATUS_RESPONSE=1;
    public static int VERSION_Serializable=1;
    
    /**
     * 来源
     */
	ClientIdentify sourceIdentify=null;
	/**
	 * 目标
	 */
    ServerIdentify targetIdentify=null;
    
    InetSocketAddress sourceAddress;
    
    /** 调用参数
			params[0]=clazz.getName();
			params[1]=serviceName;
			params[2]=signature.getName();
			params[3]=signature.getDescriptor();
			params[4...n]=args[4...n];
     */
    Object[] params=null;

	int status=0;
    

    // 当前数据包对应的关键字,即,在同一序列中的不同序列号的数据包有相同的key
    long key = 0;

    long retryKey = 0;

    // 当前数据序列号
    int pserial = 1;

    // 该数据数据量(包含多少数据包)
    int ptotal = 1;

    // 当前包含数据长度
    int datalength = 0;

    // 总共多少数据量
    long totallength = 0;

    // 当前包含数据量
    byte[] data = null;

    // 原始数据
    byte[] origData = null;
    //数据编码版本
    int version=0;
    //是否压缩
    int gzip=0;

	public static final int DATA_LENGTH = 25000;// 65400; //max is 65535-28 =
                                                // 65507

    public static final int PACK_LENGTH = 36600;

    static Random rd = new Random();

    final static Log logger=LogFactory.getLog(DataPack.class);

    /**
     * 
     * @param targetIdentify
     * @param sourceIdentify
     * @param status
     * @param params  
			params[0]=clazz.getName();
			params[1]=serviceName;
			params[2]=signature.getName();
			params[3]=signature.getDescriptor();
			params[4...n]=args[4...n];
     */
    public DataPack(ServerIdentify targetIdentify, ClientIdentify sourceIdentify,int status, Object[] params) {
        //logger = 
        this.targetIdentify=targetIdentify;
        this.sourceIdentify=sourceIdentify;
        
        this.status=status;
        this.params=params;
        this.data = processParams2Bytes(params);
        if (data.length>1000){
        	data=gzip(data);        
        }
        key = genKey();
    }

    private byte[] gzip(byte[] odata) {
    	try{
	    	ByteArrayOutputStream dataous=new ByteArrayOutputStream(1024);
			GZIPOutputStream ous=new GZIPOutputStream(dataous);
			ous.write(odata);
			ous.finish();
			ous.flush();
			ous.close();
			byte[] res=dataous.toByteArray();
			setGzip(1);
			logger.info("Zip data from "+odata.length+"->"+res.length);
			return res;
    	}catch(Exception e){
    		setGzip(0);
    		return odata;
    	}
	}

	static public long genKey() {
        int seed = Math.abs(rd.nextInt());
        int seedleng = (seed + "").length();
        int maxlength = (Long.MAX_VALUE + "").length() - 1;
        long pow = new Double(Math.pow(10, (maxlength - seedleng))).longValue();
        long res = (seed * pow);
        res += (rd.nextInt() + System.currentTimeMillis()) % pow;
        return res;
    }

    public DataPack(DatagramPacket pack) throws ArrayIndexOutOfBoundsException {
        this.origData = pack.getData();
        setSourceAddress((InetSocketAddress)pack.getSocketAddress());
        parseData();
    }

    public void parseData() {
        int startidx = 0;
        byte[] keyBytes = new byte[8];
        System.arraycopy(origData, startidx, keyBytes, 0, keyBytes.length);
        key = BytesUtils.byteToLong(keyBytes);

        startidx += keyBytes.length;
        byte[] retryKeyBytes = new byte[8];
        System.arraycopy(origData, startidx, retryKeyBytes, 0, retryKeyBytes.length);
        retryKey = BytesUtils.byteToLong(retryKeyBytes);

        
        startidx += retryKeyBytes.length;
        byte[] satusBytes = new byte[4];
        System.arraycopy(origData, startidx, satusBytes, 0, satusBytes.length);
        status = BytesUtils.byteToInt(satusBytes);
        
        
        startidx += satusBytes.length;
        byte[] recvPortBytes = new byte[4];
        System.arraycopy(origData, startidx, recvPortBytes, 0, recvPortBytes.length);
        int recvPort = BytesUtils.byteToInt(recvPortBytes);

        // port=recvPort;

        startidx += recvPortBytes.length;
        byte[] pserialbytes = new byte[4];
        System.arraycopy(origData, startidx, pserialbytes, 0, pserialbytes.length);
        pserial = BytesUtils.byteToInt(pserialbytes);

        startidx += pserialbytes.length;
        byte[] ptotalBytes = new byte[4];
        System.arraycopy(origData, startidx, ptotalBytes, 0, ptotalBytes.length);
        ptotal = BytesUtils.byteToInt(ptotalBytes);
        
        startidx += ptotalBytes.length;
        byte[] recvPortsLengthByte = new byte[4];
        System.arraycopy(origData, startidx, recvPortsLengthByte, 0, recvPortsLengthByte.length);
        int recvPortsByteLength = BytesUtils.byteToInt(recvPortsLengthByte);
        
        startidx += recvPortsLengthByte.length;
        byte[] recvPortsBytes = new byte[recvPortsByteLength];
        System.arraycopy(origData, startidx, recvPortsBytes, 0, recvPortsBytes.length);
        String identifys=new String(recvPortsBytes);
        setIdentify(identifys);
        
        startidx += recvPortsBytes.length;
        byte[] plengthBytes = new byte[4];
        System.arraycopy(origData, startidx, plengthBytes, 0, plengthBytes.length);
        datalength = BytesUtils.byteToInt(plengthBytes);

        startidx += plengthBytes.length;
        byte[] ptotalLengthBytes = new byte[4];
        System.arraycopy(origData, startidx, ptotalLengthBytes, 0, ptotalLengthBytes.length);
        totallength = BytesUtils.byteToInt(ptotalLengthBytes);

        
        startidx += ptotalLengthBytes.length;
        data = new byte[datalength];
        try {
            System.arraycopy(origData, startidx, data, 0, data.length);
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.error("Byte:" + data.length + " is excepted,but :" + (origData.length - startidx) + " is met.");
            throw e;
        }
        
        
        startidx += datalength;
        byte[] versionBytes = new byte[4];
        if (origData.length>=startidx+4){
        	System.arraycopy(origData, startidx, versionBytes, 0, versionBytes.length);
        	version = BytesUtils.byteToInt(versionBytes);
        }
        
        startidx += versionBytes.length;
        byte[] gzipBytes = new byte[4];
        if (origData.length>=startidx+4){
        	System.arraycopy(origData, startidx, gzipBytes, 0, gzipBytes.length);
        	gzip = BytesUtils.byteToInt(gzipBytes);
        	logger.info("Datapack gzip status :"+gzip);
        }
    }

    private void setIdentify(String identifys) {
		int idx=identifys.indexOf("-->");
		if (idx!=-1){
			this.sourceIdentify=new ClientIdentify(identifys.substring(0,idx));
			this.targetIdentify=new ServerIdentify(identifys.substring(idx+3));
			//this.recvPorts=sourceIdentify.getPortStr();
		}else{
			throw new RuntimeException("Bad version of Datapack");
		}
    }

	public byte[] toByteArray() {
        if (data != null) {
            byte[] recvPortsData=//recvPorts.getBytes();////FIXME remove when final export product;
            				(sourceIdentify.toString()+"-->"+targetIdentify.toString()).getBytes();
            byte[] recvPortsDataLength=BytesUtils.intToByte(recvPortsData.length);
            byte[] keybytes = BytesUtils.longToByte(key); // 8
            byte[] retryKeyBytes = BytesUtils.longToByte(retryKey); // 8;
            byte[] statusbytes = BytesUtils.intToByte(status);
            //byte[] recvportbytes = BytesUtils.intToByte(sourceIdentify.getPort());
            //FIXME if need
            byte[] recvportbytes = BytesUtils.intToByte(0);
            byte[] totallengthbyte = BytesUtils.intToByte(data.length); // 4
            byte[] versionbyte = BytesUtils.intToByte(version);
            byte[] gzipbyte = BytesUtils.intToByte(gzip);
            // byte[][] splitdatas = splitData(data); //65000
            // DatagramPacket[] grams=new DatagramPacket[splitdatas.length];
            // for (int i = 0; i < splitdatas.length; i++) {
            byte[] pdata = data;
            byte[] pserialbyte = BytesUtils.intToByte(0);
            byte[] ptotalbyte = BytesUtils.intToByte(1);
            byte[] datalengthbyte = BytesUtils.intToByte(pdata.length);
            
            // 8 8 4 4 4 4 4 65000
            byte[][] findata = new byte[][] { keybytes, retryKeyBytes,statusbytes, recvportbytes, pserialbyte, ptotalbyte,recvPortsDataLength,recvPortsData, datalengthbyte, totallengthbyte, pdata,versionbyte,gzipbyte };
            				 //new byte[][] { keybytes, retryKeyBytes,statusbytes, recvportbytes, pserialbyte, ptotalbyte,recvPortsDataLength,recvPortsData, datalengthbyte, totallengthbyte, pdata };
            //byte[][] findata = new byte[][] { keybytes, retryKeyBytes,statusbytes, recvportbytes, pserialbyte, ptotalbyte,recvPortsDataLength,recvPortsData, datalengthbyte, totallengthbyte, pdata ,versionbyte};
            // int
            // leng=keybytes.length+pserialbyte.length+ptotalbyte.length+datalengthbyte.length+totallengthbyte.le+pdata.length;
            byte[] gramdata = joinByteArray(findata);
            // grams[i].setData(gramdata);
            return gramdata;

        }
        return null;
    }
	public byte[][] split() {
        if (data != null) {
            byte[] recvPortsData=//recvPorts.getBytes();////FIXME remove when final export product;
            				    (sourceIdentify.toString()+"-->"+targetIdentify.toString()).getBytes();
            byte[] recvPortsDataLength=BytesUtils.intToByte(recvPortsData.length);
            
            byte[] keybytes = BytesUtils.longToByte(key); // 8
            byte[] retryKeyBytes = BytesUtils.longToByte(retryKey); // 8;
            byte[] statusbytes = BytesUtils.intToByte(status);
            // byte[] recvportbytes = BytesUtils.intToByte(sourceIdentify.getPort());
            byte[] recvportbytes = BytesUtils.intToByte(0);
            byte[] totallengthbyte = BytesUtils.intToByte(data.length); // 4
            byte[] versionbyte = BytesUtils.intToByte(version);
            byte[] gzipbyte = BytesUtils.intToByte(gzip);
            byte[][] splitdatas = splitData(data); // 65000
            byte[][] grams = new byte[splitdatas.length][];
            for (int i = 0; i < splitdatas.length; i++) {
                byte[] pdata = splitdatas[i];
                byte[] pserialbyte = BytesUtils.intToByte(i);
                byte[] ptotalbyte = BytesUtils.intToByte(splitdatas.length);
                byte[] datalengthbyte = BytesUtils.intToByte(pdata.length);
                // 8 8 4 4 4 4 4 65000
                byte[][] findata = new byte[][] { keybytes, retryKeyBytes,statusbytes, recvportbytes, pserialbyte, ptotalbyte,recvPortsDataLength,recvPortsData, datalengthbyte, totallengthbyte, pdata ,versionbyte,gzipbyte};
                // int
                // leng=keybytes.length+pserialbyte.length+ptotalbyte.length+datalengthbyte.length+totallengthbyte.le+pdata.length;
                byte[] gramdata = joinByteArray(findata);
                
                //InetSocketAddress addr=new InetSocketAddress(targetIdentify.getHost(),targetIdentify.getPort());
				grams[i] = gramdata;
				//grams[i] = new DatagramPacket()
				
            }
            return grams;
        }
        return null;
    }
    public DatagramPacket[] splitDataGram() {
        if (data != null) {
            byte[] recvPortsData=//recvPorts.getBytes();////FIXME remove when final export product;
            				    (sourceIdentify.toString()+"-->"+targetIdentify.toString()).getBytes();
            byte[] recvPortsDataLength=BytesUtils.intToByte(recvPortsData.length);
            
            byte[] keybytes = BytesUtils.longToByte(key); // 8
            byte[] retryKeyBytes = BytesUtils.longToByte(retryKey); // 8;
            byte[] statusbytes = BytesUtils.intToByte(status);
            // byte[] recvportbytes = BytesUtils.intToByte(sourceIdentify.getPort());
            byte[] recvportbytes = BytesUtils.intToByte(0);
            byte[] totallengthbyte = BytesUtils.intToByte(data.length); // 4
            byte[] versionbyte = BytesUtils.intToByte(version);
            byte[] gzipbyte = BytesUtils.intToByte(gzip);
            
            byte[][] splitdatas = splitData(data); // 65000
            DatagramPacket[] grams = new DatagramPacket[splitdatas.length];
            for (int i = 0; i < splitdatas.length; i++) {
                byte[] pdata = splitdatas[i];
                byte[] pserialbyte = BytesUtils.intToByte(i);
                byte[] ptotalbyte = BytesUtils.intToByte(splitdatas.length);
                byte[] datalengthbyte = BytesUtils.intToByte(pdata.length);
                // 8 8 4 4 4 4 4 65000
                byte[][] findata = new byte[][] { keybytes, retryKeyBytes,statusbytes, recvportbytes, pserialbyte, ptotalbyte,recvPortsDataLength,recvPortsData, datalengthbyte, totallengthbyte, pdata ,versionbyte,gzipbyte};
                // int
                // leng=keybytes.length+pserialbyte.length+ptotalbyte.length+datalengthbyte.length+totallengthbyte.le+pdata.length;
                byte[] gramdata = joinByteArray(findata);
                
                //InetSocketAddress addr=new InetSocketAddress(targetIdentify.getHost(),targetIdentify.getPort());
				grams[i] = new DatagramPacket(gramdata, gramdata.length);
				//grams[i] = new DatagramPacket()
				
            }
            return grams;
        }
        return null;
    }

    // keybytes, byte[] pserialbyte, byte[] ptotalbyte, byte[] datalengthbyte,
    // byte[] totallengthbyte, byte[] pdata
    private byte[] joinByteArray(byte[][] splitdata) {
        int length = 0;
        for (int i = 0; i < splitdata.length; i++) {
            length += splitdata[i].length;
        }
        byte[] gramdata = new byte[length];
        int startidx = 0;
        for (int i = 0; i < splitdata.length; i++) {
            System.arraycopy(splitdata[i], 0, gramdata, startidx, splitdata[i].length);
            startidx += splitdata[i].length;
        }
        return gramdata;
    }

    private byte[][] splitData(byte[] data) {
        int floor = data.length % DATA_LENGTH;
        int size = data.length / DATA_LENGTH;
        if (floor > 0)
            size += 1;
        byte[][] splits = new byte[size][];
        int startidx = 0;
        for (int i = 0; i < size; i++) {
            if (data.length - startidx > DATA_LENGTH) {
                byte[] part = new byte[DATA_LENGTH];
                System.arraycopy(data, startidx, part, 0, part.length);
                splits[i] = part;
                startidx += part.length;
            } else {// last packet.
                byte[] part = new byte[data.length - startidx];
                System.arraycopy(data, startidx, part, 0, part.length);
                splits[i] = part;
                startidx += part.length;
            }
        }
        return splits;
    }

    public void addDataPack(DataPack dp) {
        byte[] fdata = new byte[data.length + dp.getBytesData().length];
        System.arraycopy(data, 0, fdata, 0, data.length);
        byte[] ndata = dp.getBytesData();
        System.arraycopy(ndata, 0, fdata, data.length, ndata.length);
        datalength = fdata.length;
        data = fdata;
    }

    public byte[] getOrigData() {
        return origData;
    }

    public int getDatalength() {
        return datalength;
    }

    public byte[] getBytesData() {
        return data;
    }
    /**
     * 返回结果远程调用的结果数据
     * @return 调用参数 <br/>
		params[0]=clazz.getName();<br/>
		params[1]=serviceName;<br/>
		params[2]=signature.getName();<br/>
		params[3]=signature.getDescriptor();<br/>
		params[4...n]=args[4...n];<br/>
     */
    public Object[] getParams(){
    	synchronized (this) {
    		if (params==null)
    			params=parseData2Objects();
		}
    	return params;
    }
    
    private Object[] parseData2Objects() {
		if (gzip==1){
			data=ungzip(data);
		}
		return serializableDecode(data);
	}
    private byte[] ungzip(byte[] odata) {
    	try{
	    	ByteArrayInputStream byteins=new ByteArrayInputStream(odata);
			GZIPInputStream gzipins=new GZIPInputStream(byteins, 1024);
			ByteArrayOutputStream res=new ByteArrayOutputStream();
			byte[] buff=new byte[1024];
			int leng=gzipins.read(buff);
			while (leng>0){
				res.write(buff,0,leng);
				leng=gzipins.read(buff);	
			}
			gzipins.close();
			res.flush();
			return res.toByteArray();
    	}catch(Exception e){
    		logger.error("Cannot unzip datapack",e);
    		return odata;
    	}
	}

	private byte[] processParams2Bytes(Object[] params){
    	boolean iss=true; 
		byte[] res= null;
		for (Object o:params){
			if (!(o instanceof Serializable)){
				iss=false;
			}
		}
    	if (iss)
    		res=serialize(params);
		return res;
		
    }
    
	public InetSocketAddress getSourceAddress() {
		return sourceAddress;
	}

	public void setSourceAddress(InetSocketAddress sourceAddress) {
		this.sourceAddress = sourceAddress;
	}

	public int getPserial() {
        return pserial;
    }

    public int getPtotal() {
        return ptotal;
    }

    public long getTotallength() {
        return totallength;
    }

    public long getKey() {
        return key;
    }

    public void setKey(long key) {
        this.key = key;
    }
	
    public long getRetryKey() {
        return retryKey;
    }

    public void setRetryKey(long retryKey) {
        this.retryKey = retryKey;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public ClientIdentify getSourceIdentify() {
        return sourceIdentify;
    }

    public void setSourceIdentify(ClientIdentify sourceIdentify) {
        this.sourceIdentify = sourceIdentify;
    }

	public int getVersion() {
		return version;
	}

    public void setVersion(int version) {
		this.version = version;
	}
    
    public int getGzip() {
		return gzip;
	}

	public void setGzip(int gzip) {
		this.gzip = gzip;
	}

	public ServerIdentify getTargetIdentify() {
		return targetIdentify;
	}

	public void setTargetIdentify(ServerIdentify targetIdentify) {
		this.targetIdentify = targetIdentify;
	}
	

	private Object[] serializableDecode(byte[] data) {
		try {
			// ObjectInputStream ins;
			SerializableInput objIns = new SerializableInput(new ByteArrayInputStream(data), Thread.currentThread().getContextClassLoader());
			return (Object[]) objIns.readObject();
		} catch (IOException e) {
			logger.error("", e);
		} catch (ClassNotFoundException e) {
			logger.error("", e);
		}
		return null;
	}

	
    private byte[] serialize(Object[] params) {
    	byte[] res=null;
    	try {
			ByteArrayOutputStream ous=new ByteArrayOutputStream(1024);
			ObjectOutputStream oos=new ObjectOutputStream(ous);
//			Object[] params=new Object[4+args.length];
//			params[0]=clazz.getName();
//			params[1]=serviceName;
//			params[2]=signature.getName();
//			params[3]=signature.getDescriptor();
//			for (int i=4;i<params.length;i++){
//	            params[i]=args[i-4];
//	        }
			oos.writeObject(params);
			oos.flush();
			return ous.toByteArray();
		} catch (NotSerializableException e) {
			logger.error("",e);
		} catch (IOException e) {
			logger.error("",e);
		}
    	return res;
    }
}
