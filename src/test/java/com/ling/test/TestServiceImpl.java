package com.ling.test;

import java.util.Random;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TestServiceImpl implements TestService {
	Log logger=LogFactory.getLog(TestServiceImpl.class);
	Random rd=new Random();
	String key="default";
	public TestServiceImpl(){
		logger.info("TestServiceImpl inited.");
	}
	@Override
	public String getContent(String cachekey) {
		return key+"_"+Math.abs(rd.nextInt());
	}

	@Override
	public boolean refresh(String key) {
		this.key=key;
		return true;
	}

}
