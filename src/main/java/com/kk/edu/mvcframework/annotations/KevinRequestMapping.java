package com.kk.edu.mvcframework.annotations;

import java.lang.annotation.*;

@Documented
@Target({ElementType.TYPE, ElementType.METHOD})  // 可用于类、接口或方法上
@Retention(RetentionPolicy.RUNTIME)
public @interface KevinRequestMapping {

    String value() default "";

}
