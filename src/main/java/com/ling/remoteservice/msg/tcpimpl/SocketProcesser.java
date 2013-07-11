package com.ling.remoteservice.msg.tcpimpl;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ling.remoteservice.host.ClientIdentify;
import com.ling.remoteservice.msg.DataPack;
import com.ling.remoteservice.utils.BytesUtils;

public class SocketProcesser implements ISocketProcesser{
	static Log logger=LogFactory.getLog(SocketProcesser.class);

	boolean needHealthCheck=false;
	boolean stop=false;
	
	protected SocketManager socketManager;
	protected SocketChannel socketChannel;
	//private BlockingQueue<DataPack> sendQueue;
	private String targetServer;
	private InetSocketAddress remoteAddress;
	private String socketIdentify;
	
	private Reader reader;//=new Reader();
	ConnectionPool pool;
	boolean client;
	/**
	 * 
	 * @param socketManager
	 * @param socketChannel 
	 * @param client , true means its connect from client size to a server.  in this case , client need to send register package first.
	 *                 false means its a server socketchannel connected by client，
	 */
	public SocketProcesser(SocketManager socketManager, ConnectionPool pool, SocketChannel socketChannel, boolean client) throws IOException {
		reader=new Reader();
		this.socketManager=socketManager;
		this.pool=pool;
		if (pool!=null)
			setTargetServer(pool.getTargetIdentify());
		this.socketChannel=socketChannel;
		this.client=client;

		try {
			socketChannel.socket().setTcpNoDelay(true);
			//强制关闭
			socketChannel.socket().setSoLinger(true, 0);
			socketChannel.socket().setSoTimeout(3000);
			socketChannel.socket().setPerformancePreferences(1, 3, 2);
		} catch (SocketException e) {
			logger.error("",e);
		}
		
		socketIdentify=socketChannel.toString();
		try {
			remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
		} catch (IOException e) {
			logger.error("",e);
		}
		
		socketManager.getSelector().registChannel(this);
		if (client){
			try {
				sendData(getRegistDataPack());
			} catch (Exception e) {
				logger.error("Cannot send ",e);
			}
		}
	}

	public InetSocketAddress getRemoteAddress(){
		return remoteAddress;
	}
	protected byte[] getRegistDataPack(){
		ClientIdentify localIdentify=socketManager.getLocalIdentify();
		
		byte[] targetString=localIdentify.toString().getBytes();
		byte[] pack=new byte[datapackRegist.length+targetString.length];
		System.arraycopy(datapackRegist, 0, pack, 0, datapackRegist.length);
		System.arraycopy(targetString, 0, pack, datapackRegist.length, targetString.length);
		return pack;
	}
	public void sendData(DataPack sendData) throws IOException{
		long start=System.currentTimeMillis();
		
		if (sendData==null) return;
		try {
			logger.info("send datapack:"+sendData.getKey()+" from ["+sendData.getSourceIdentify()+"] target ["+sendData.getTargetIdentify()+"] socket["+socketIdentify+"] executeDataSendDate["+new Date(sendData.getRetryKey())+"]");
			byte[][] datas=sendData.split();
			for (byte[] pack:datas)
				sendData(pack);
			logger.info("cost time send "+(sendData==null?"":sendData.getKey())+":"+(System.currentTimeMillis()-start));
		} catch (InterruptedException e) {
			logger.error("",e);
		}catch(IOException e){
			throw e;
		}catch(Exception e){
			logger.error("",e);
		}
	}
	
	protected byte[] getHealthCheckDataPack() {
		return datapackHealth;
	}
	
	synchronized void  sendData(byte[] sendData) throws Exception{
		//logger.info("send data:"+new String(sendData));
		while ((socketChannel.validOps()& SelectionKey.OP_WRITE)==0){
			logger.error("Wait until socketChannel ["+socketChannel+"] is writable.");
			Thread.sleep(100);
		}
		ByteBuffer buffer=ByteBuffer.allocate(datapackStart.length+datapackEnd.length+4+sendData.length);
		buffer.put(datapackStart);
		buffer.put(BytesUtils.intToByte(sendData.length));
		buffer.put(sendData);
		buffer.put(datapackEnd);
		
		//logger.info("write ["+socketChannel+"]"+targetServer+" :"+buffer.toString()+":"+new String(buffer.array(),0,buffer.limit()));
		buffer.flip();
		int writeSize=0;
		int count=0;
		int max_send=1000;
		while (count<max_send && buffer.remaining() >0){
			int wsize=socketChannel.write(buffer);
			if (wsize==0)
				count++;
			else
				count=0;
			writeSize+=wsize;
		}
		if (count>=max_send){
			logger.error("Try flush channel for sending issues for:"+socketChannel);
			flushChannel(socketChannel,buffer,3000);
			if (buffer.remaining()>0){
				logger.error("Can't write Socketchannel ["+socketChannel
					+"] isConnected["+socketChannel.isConnected()
					+"],buffer limit["+buffer.limit()+"]remaining["+buffer.remaining()+"],socketchannel write size["+writeSize+"]"
					+"],and write 0 byte for 10000 times.\nTry close this socket channel.");
				throw new IOException("Write fault at ["+socketChannel+"] for 10000 times.");
			}
		}
	}
	long flushChannel(SocketChannel socketChannel,ByteBuffer bb, long writeTimeout) throws IOException{
	    SelectionKey key = null;
	    Selector writeSelector = null;
	    int attempts = 0;
	    int bytesProduced = 0;
	    try {
	        while (bb.hasRemaining()) {
	            int len = socketChannel.write(bb);
	            attempts++;
	            if (len < 0){
	                throw new EOFException();
	            }
	            bytesProduced += len;
	            if (len == 0) {
	                if (writeSelector == null){
	                    writeSelector = SelectorFactory.getSelector();
	                    if (writeSelector == null){
	                        // Continue using the main one
	                        continue;
	                    }
	                }
	                key = socketChannel.register(writeSelector, SelectionKey.OP_WRITE);
	                if (writeSelector.select(writeTimeout) == 0) {
	                    if (attempts > 2)
	                        throw new IOException("Client disconnected");
	                } else {
	                    attempts--;
	                }
	            } else {
	                attempts = 0;
	            }
	        }
	    } finally {
	        if (key != null) {
	            key.cancel();
	            key = null;
	        }
	        if (writeSelector != null) {
	            // Cancel the key.
	            writeSelector.selectNow();
	            SelectorFactory.returnSelector(writeSelector);
	        }
	    }
	    return bytesProduced;

	} 
	
	public void readData(SelectionKey key) throws IOException{
		ByteBuffer buffer2 = ByteBuffer.allocate(4096);
		ByteArrayOutputStream ous=new ByteArrayOutputStream(8192);
		SocketChannel socketchannel=(SocketChannel)key.channel();
		while (socketchannel.read(buffer2) > 0){
			buffer2.flip();
			byte[] readbytes=new byte[buffer2.limit()];
			buffer2.get(readbytes);
			ous.write(readbytes);
			buffer2.clear();
		}
		byte[] read=ous.toByteArray();
		if (read.length==0){
			 key.interestOps(SelectionKey.OP_READ);
		}else{
			byte[] pack=reader.append(read);
			while (pack!=null){
				processPack(socketchannel,pack);
				pack=reader.append(new byte[0]); //避免接收多个package后，只处理当前pack
			}
		}
	}
	
	
	public void processPack(SocketChannel socketChannel,byte[] recvData) throws IOException {
		if (expectData(recvData, ISocketProcesser.datapackHealth)) {
			logger.info("expect health check pack from :"+socketChannel);
		}else{
			if (expectData(recvData,ISocketProcesser.datapackRegist)){
				
				int leng=ISocketProcesser.datapackRegist.length;
				String remoteIdent=new String(recvData,leng,recvData.length-leng);
				logger.info("expect regist ["+remoteIdent+"] data pack from :"+socketChannel);
				int hostsidx=remoteIdent.indexOf("://");
				int hosteidx=remoteIdent.indexOf(":",hostsidx+3);
				String remoteHost=remoteIdent.substring(hostsidx+3,hosteidx);
				if ("localhost".equals(remoteHost.toLowerCase())){
					InetSocketAddress raddr= (InetSocketAddress) socketChannel.getRemoteAddress();
					remoteIdent=remoteIdent.substring(0, hostsidx+3)+raddr.getHostString()+remoteIdent.substring(hosteidx);
					logger.info("remoteIdent has change to ["+remoteIdent+"] data pack from :"+socketChannel);
				}
				
				//TODO different when it's server?
				ConnectionPool cp=socketManager.getProcesserList(remoteIdent);
				if (cp==null){
					cp= socketManager.createProcesserList(remoteIdent, false);
				}
				cp.put(socketChannel.toString(), this);
				this.setTargetServer(remoteIdent);
				
			}else{
				
				DatagramPacket dp = new DatagramPacket(recvData, recvData.length);
				dp.setSocketAddress(socketChannel.socket().getRemoteSocketAddress());
				
				socketManager.addRecvData(dp);
				if (logger.isInfoEnabled()){
					DataPack pack=new DataPack(dp);
					logger.info("recv data pack:"+pack.getKey()+" from "+pack.getSourceIdentify()+" socket address:"+socketChannel.socket().getRemoteSocketAddress());
				}
			}
		}
	}
	
	protected boolean expectData(byte[] data, byte[] dataRegist) {

		int readIdx = 0;
		int b = data[readIdx];
		if (b == dataRegist[0]) {
			for (int j = 0; j < dataRegist.length; j++) {
				if (b == dataRegist[j]) {
					if (j == dataRegist.length - 1) {
						return true;
					} else {
						readIdx++;
						b = data[readIdx];
					}
				} else {
					return false;
				}
			}
		}
		return false;
	}


	public void close() {
		//setStop();
		try {
			logger.error("close socket:"+socketChannel);
			socketChannel.close();
		} catch (IOException e) {
			logger.error("",e);
		}
	}
	
	public String getSocketIdentify() {
		return socketIdentify;
	}

	public SocketChannel getSocketChannel() {
		return socketChannel;
	}

	public String getTargetServer() {
		return targetServer;
	}
	
	public void setTargetServer(String targetIdentify) {
		this.targetServer=targetIdentify;
	}

}



class Reader{
	byte[] original=new byte[0];
	//byte[] fullpack=new byte[0];
	int checkPos=0;
	int packStartPos=0;
	int datalength=-1;
	int packCheckStartPos=0;
	//boolean readPack=false;
	public void reset(){
		//fullpack=new byte[0];
		checkPos=0;
		packStartPos=0;
		datalength=-1;
		packCheckStartPos=0;
		//readPack=false;
	}
	public byte[] append(byte[] bytes){
		byte[] temp=new byte[bytes.length+original.length];
		System.arraycopy(original, 0, temp, 0, original.length);
		System.arraycopy(bytes, 0, temp, original.length, bytes.length);
		original=temp;
		if (expectStart()){ //数据包开始
			if (datalength==-1){
				if ((original.length-checkPos)>=4){
					byte[] dleng=new byte[4];
					System.arraycopy(original, checkPos, dleng, 0, 4);
					checkPos+=4;
					datalength=BytesUtils.byteToInt(dleng);
				}
			}
			if (datalength!=-1){
				if ((original.length-checkPos)>=datalength){
					byte[] fullpack=new byte[datalength];
					System.arraycopy(original, checkPos, fullpack, 0, datalength);
					checkPos+=datalength;
					
					byte[] res=new byte[original.length-checkPos];
					if (res.length>0){
						System.arraycopy(original, checkPos, res, 0, res.length);
					}
					original=res;
					//logger.info("read datapack end. data["+fullpack.length+"],rest bytes["+res.length+"]");
					//readPack=true;
					reset();
					return fullpack;
				}else{
					//int currentload=original.length-checkPos;
					//logger.info("Expect data pack length ["+datalength+"] current received "+currentload+" progress "+currentload*100/datalength+"%");
				}
			}
		}
		return null;
	}
	private boolean expectStart() {
		if (packCheckStartPos==ISocketProcesser.datapackStart.length)
			return true;
		for (int i=checkPos;i<original.length;i++){
			checkPos++;
			if (match(original[i])){
				packStartPos=checkPos;
				return true;
			}
		}
		//logger.info("not any byte readed.");
		return false;
	}
	private boolean match(byte b){
		if (b == ISocketProcesser.datapackStart[packCheckStartPos]){
			packCheckStartPos++;
			if (packCheckStartPos==ISocketProcesser.datapackStart.length){
				return true;
			}
		}else{
			if (b == ISocketProcesser.datapackStart[0]){
				packCheckStartPos=1;
			}else{
				packCheckStartPos=0;
			}
		}
		return false;
	}
}