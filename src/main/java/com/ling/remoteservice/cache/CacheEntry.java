package com.ling.remoteservice.cache;

import java.io.ByteArrayInputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import com.ling.remoteservice.utils.SerializableInput;

public class CacheEntry<T> implements Serializable {
	private static final long serialVersionUID = -1386111157516104576L;

	public CacheEntry(long exptime, T content) {
		this.expireTime = exptime;
		this.cacheTime = System.currentTimeMillis();
		this.content = content;
	}

	long expireTime = 0;
	long cacheTime = 0;
	T content;

	public boolean isExpired() {
		if (expireTime < 0) {
			return false;
		}
		if (expireTime == 0)
			return true;
		if (expireTime > 0) {
			return (expireTime + cacheTime) < System.currentTimeMillis();
		}
		return false;
	}

	public T getContent() {
		return content;
	}

	public void setContent(T content) {
		this.content = content;
	}

	public long getExpireTime() {
		return expireTime;
	}

	public void setExpireTime(long expireTime) {
		this.expireTime = expireTime;
	}

	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
		//stream.read
		//
		this.expireTime=stream.readLong();
		this.cacheTime=stream.readLong();
		int leng=stream.readInt();

		byte[] dataBytes=new byte[leng];
		stream.readFully(dataBytes);

		SerializableInput objIns = new SerializableInput(new ByteArrayInputStream(dataBytes), Thread.currentThread().getContextClassLoader());
		this.content=(T)objIns.readObject();
	}

	private void writeObject(ObjectOutputStream stream) throws IOException {
		ByteArrayOutputStream odata=new ByteArrayOutputStream();
		ObjectOutputStream ous=new ObjectOutputStream(odata);
		ous.writeObject(content);
		ous.flush();
		odata.flush();
		byte[] databyte=odata.toByteArray();

		stream.writeLong(expireTime);
		//System.out.println(stream.);
		stream.writeLong(cacheTime);
		stream.writeInt(databyte.length);
		stream.write(databyte);
		stream.flush();
	}


}