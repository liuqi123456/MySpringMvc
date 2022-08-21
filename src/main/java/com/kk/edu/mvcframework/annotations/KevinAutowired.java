package com.kk.edu.mvcframework.annotations;

import java.lang.annotation.*;

@Documented
@Target({ElementType.FIELD})  // 可用于域上，用于描述域
@Retention(RetentionPolicy.RUNTIME)
public @interface KevinAutowired {

    String value() default "";

}
