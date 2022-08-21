package com.kk.demo.service.impl;

import com.kk.demo.service.IDemoService;
import com.kk.edu.mvcframework.annotations.KevinService;

@KevinService("demoService")
public class DemoServiceImpl implements IDemoService {

    @Override
    public String get(String name) {
        System.out.println("service 实现类中的name参数：" + name) ;
        return name;
    }

}
