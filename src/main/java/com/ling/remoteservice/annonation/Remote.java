package com.ling.remoteservice.annonation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)    
@Target(value={ElementType.TYPE,ElementType.METHOD})
public @interface Remote {
    String serviceName();
    long timeout() default 6000;
    int retry() default 2;
    int connectionLimit() default 30;
}
