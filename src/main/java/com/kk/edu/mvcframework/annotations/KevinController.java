package com.kk.edu.mvcframework.annotations;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE) // 用于设定注解使用范围，ElementType.TYPE标明该注解可用于类或者接口上，用于描述类、接口(包括注解类型) 或enum声明
@Retention(RetentionPolicy.RUNTIME)// 指定生存周期，运行时有效
public @interface KevinController {

    String value() default "";

}
