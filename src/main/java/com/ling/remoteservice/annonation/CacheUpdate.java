package com.ling.remoteservice.annonation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)    
@Target(ElementType.METHOD)
public @interface CacheUpdate {
    String[] cacheName();
    String[] updateKey();
    //String[] updateMethod() default {};
    boolean diskCache() default false;
}
