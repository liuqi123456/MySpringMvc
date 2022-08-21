package com.kk.demo.service.impl;

import com.kk.demo.service.IDemoService;
import com.kk.edu.mvcframework.annotations.KevinService;

@KevinService("demoService")
public class DemoServiceImpl implements IDemoService {

    @Override
    public String get(String name) {
        System.out.println("service impl is name = " + name) ;
        return name;
    }

}
