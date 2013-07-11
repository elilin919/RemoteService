package com.ling.remoteservice.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Array;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SerializableInput extends ObjectInputStream {
	ClassLoader loader;
	static Log logger=LogFactory.getLog(SerializableInput.class);
	public SerializableInput(InputStream in,ClassLoader loader) throws IOException {
		super(in);
		if (loader!=null)
			this.loader=loader;
	}
	public void setClassLoader(ClassLoader loader){
		this.loader=loader;
	}
	protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException{
		try {
			return super.resolveClass(desc);
		} catch (ClassNotFoundException ex) {
			String name = desc.getName();
			if (name.startsWith("[L")){
				name=name.substring(2,name.length()-1);
				Class cls=loader.loadClass(name);
				return Array.newInstance(cls, 0).getClass();
			}else{
				return loader.loadClass(name);
			}
			
		}
    }
}
