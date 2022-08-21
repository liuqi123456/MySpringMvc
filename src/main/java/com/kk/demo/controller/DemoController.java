package com.kk.demo.controller;

import com.kk.demo.service.IDemoService;
import com.kk.edu.mvcframework.annotations.KevinAutowired;
import com.kk.edu.mvcframework.annotations.KevinController;
import com.kk.edu.mvcframework.annotations.KevinRequestMapping;
import com.kk.edu.mvcframework.annotations.KevinRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@KevinController
@KevinRequestMapping("/MySpringMvc")
public class DemoController {

    @KevinAutowired
    private IDemoService demoService;

    @KevinRequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response,
                        @KevinRequestParam(value = "name") String name) {
        String _name = demoService.get(name);
        try {
            response.getWriter().write(_name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @KevinRequestMapping("/add")
    public void add(HttpServletRequest request, HttpServletResponse response,
                      @KevinRequestParam(value = "b") String a, @KevinRequestParam(value = "b") String b) {
        try {
            response.getWriter().write(a + b);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
