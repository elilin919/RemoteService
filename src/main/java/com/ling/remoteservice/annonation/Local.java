package com.ling.remoteservice.annonation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)    
@Target(ElementType.TYPE)
public @interface Local {
    String implementClass();
    String instanceMethod() default "";
    int executeLimit() default 120;
}   
