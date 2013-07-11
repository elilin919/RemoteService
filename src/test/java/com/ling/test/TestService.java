package com.ling.test;

import com.ling.remoteservice.annonation.Cachable;
import com.ling.remoteservice.annonation.CacheUpdate;
import com.ling.remoteservice.annonation.Local;
import com.ling.remoteservice.annonation.Remote;

@Remote(serviceName="content",connectionLimit=30)
@Local(implementClass="com.ling.test.TestServiceImpl")
public interface TestService {
	@Cachable(cacheName={"content"},cacheKey={"$params[0]"},expireTime={60000},cacheSize={100})
	public String getContent(String cachekey);
	
	@CacheUpdate(cacheName={"content"},updateKey={"$params[0]"})
	public boolean refresh(String key);
}
