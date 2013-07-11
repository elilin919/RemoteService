package com.ling.remoteservice.annonation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)    
@Target(ElementType.METHOD)
public @interface Cachable {
    String[] cacheName();
    int[] cacheSize() ;
    long[] expireTime();
    String[] cacheKey();
    
    
    boolean diskCache() default false;
    /**
     * return only cached elements. otherwise return null.
     * @return
     */
    boolean cacheOnly() default false;
    boolean updateOnly() default false;
    int limitBlockSize() default -1;
    boolean cacheNull() default false;
    boolean useExpiredCache() default false;
}
