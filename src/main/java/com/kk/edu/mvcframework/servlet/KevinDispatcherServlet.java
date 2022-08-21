package com.kk.edu.mvcframework.servlet;

import com.kk.edu.mvcframework.annotations.KevinAutowired;
import com.kk.edu.mvcframework.annotations.KevinController;
import com.kk.edu.mvcframework.annotations.KevinRequestMapping;
import com.kk.edu.mvcframework.annotations.KevinService;
import com.kk.edu.mvcframework.pojo.Handler;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KevinDispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();

    // 缓存扫描到的类的全限定类名
    private List<String> classNames = new ArrayList<>();

    // ioc容器
    private Map<String, Object> ioc = new HashMap<>();

    // handlerMapping
    private List<Handler> handlerMapping = new ArrayList<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1 加载配置文件 springmvc.properties
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        doLoadConfig(contextConfigLocation);

        // 2 扫描相关的类，扫描注解
        doScan(properties.getProperty("scanPackage"));

        // 3 初始化bean对象（实现ioc容器，基于注解）
        doInstance();

        // 4 实现依赖注入
        doAutoWired();

        // 5 构造一个HandlerMapping处理器映射器，将配置好的url和Method建立映射关系
        initHandlerMapping();

        System.out.println("kevin mvc on start ....");

        // 6 等待请求进入，处理请求
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        /**
        // 处理请求：根据url，找到对应的Method方法，进行调用
        // 获取uri
        String requestURI = req.getRequestURI();
        Method method = handlerMapping.get(requestURI);// 获取到一个反射的方法
        // 反射调用，需要传入对象，需要传入参数，此处无法完成调用，没有把对象缓存起来，也没有参数！！！！改造initHandlerMapping();
        method.invoke();
         */

        // 根据uri获取到能够处理当前请求的hanlder（从handlermapping中（list））
        Handler handler = getHandler(req);
        // handler为空则返回：404 not found
        if (handler == null) {
            resp.getWriter().write("404 not found");
            return;
        }
        // 参数绑定
        // 获取所有参数类型数组，这个数组的长度就是我们最后要传入的args数组的长度
        Class<?>[] parameterTypes = handler.getMethod().getParameterTypes();
        // 根据上述数组长度创建一个新的数组（参数数组，是要传入反射调用的）
        Object[] paraValues = new Object[parameterTypes.length];
        // 以下就是为了向参数数组中塞值，而且还得保证参数的顺序和方法中形参顺序一致
        Map<String, String[]> parameterMap = req.getParameterMap();
        // 遍历request中所有参数  （填充除了request，response之外的参数）
        for (Map.Entry<String,String[]> param: parameterMap.entrySet()) {
            // name=1&name=2   name [1,2]
            String value = StringUtils.join(param.getValue(), ",");  // 如同 1,2
            // 如果参数和方法中的参数匹配上了，填充数据
            if (!handler.getParamIndexMapping().containsKey(param.getKey())) {
                continue ;
            }
            // 方法形参确实有该参数，找到它的索引位置，对应的把参数值放入paraValues
            Integer index = handler.getParamIndexMapping().get(param.getKey());//name在第 2 个位置
            paraValues[index] = value;  // 把前台传递过来的参数值填充到对应的位置去
        }
        int requestIndex = handler.getParamIndexMapping().get(HttpServletRequest.class.getSimpleName()); // 0
        paraValues[requestIndex] = req;
        int responseIndex = handler.getParamIndexMapping().get(HttpServletResponse.class.getSimpleName()); // 1
        paraValues[responseIndex] = resp;
        // 最终调用handler的method属性
        try {
            handler.getMethod().invoke(handler.getController(), paraValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            log("加载配置文件失败");
            e.printStackTrace();
        } finally {
            if (null != resourceAsStream) {
                try {
                    resourceAsStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doScan(String scanPackage) {
        String scanPackagePath = Thread.currentThread().getContextClassLoader().getResource("").getPath() + scanPackage.replaceAll("\\.", "/");
        File pack = new File(scanPackagePath);
        File[] files = pack.listFiles();
        if (files != null && files.length > 0) {
            for (File file: files) {
                if(file.isDirectory()) { // 子package
                    // 递归
                    doScan(scanPackage + "." + file.getName());  // com.kevin.demo.controller
                } else if(file.getName().endsWith(".class")) {
                    String className = scanPackage + "." + file.getName().replaceAll(".class", "");
                    classNames.add(className);
                }
            }
        }
    }

    /**
     * ioc容器：基于classNames缓存的类的全限定类名，以及反射技术，完成对象创建和管理
     */
    private void doInstance()  {
        if (classNames.size() == 0) {
            return ;
        }
        try{
            for (int i = 0; i < classNames.size(); i++) {
                String className =  classNames.get(i);  // com.kevin.demo.controller.DemoController
                // 反射
                Class<?> aClass = Class.forName(className);
                // 区分controller，区分service'
                if(aClass.isAnnotationPresent(KevinController.class)) {
                    // controller的id此处不做过多处理，不取value了，就拿类的首字母小写作为id，保存到ioc中
                    String simpleName = aClass.getSimpleName();// DemoController
                    String beanName = lowerFirst(simpleName); // demoController
                    if (ioc.containsKey(beanName)) {
                        throw new Exception("The beanName is Defined");
                    }
                    ioc.put(beanName, aClass.newInstance());
                } else if(aClass.isAnnotationPresent(KevinService.class)) {
                    KevinService annotation = aClass.getAnnotation(KevinService.class);
                    String beanName = annotation.value(); // 获取注解value值
                    if ("".equals(beanName.trim())) {// 如果没有指定，就以类名首字母小写。如果指定了id，就以指定的为准。
                        beanName = lowerFirst(aClass.getSimpleName());
                    }
                    if (ioc.containsKey(beanName)) {
                        throw new Exception("The beanName is Defined");
                    }
                    ioc.put(beanName, aClass.newInstance());
                    // service层往往是有接口的，面向接口开发，此时再以接口名为id，放入一份对象到ioc中，便于后期根据接口类型注入
                    Class<?>[] interfaces = aClass.getInterfaces();
                    for (int j = 0; j < interfaces.length; j++) {
                        Class<?> anInterface = interfaces[j];
                        String _beanName = anInterface.getName();
                        if (ioc.containsKey(_beanName)) {
                            throw new Exception("The beanName is Defined");
                        }
                        ioc.put(_beanName, aClass.newInstance());// 以接口的全限定类名作为id放入
                    }
                } else {
                    continue;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doAutoWired() {
        // 如果对象为空，则直接返回
        if (ioc.isEmpty()) {
            return ;
        }
        // 有对象，则进行依赖注入处理

        // 遍历ioc中所有对象，查看对象中的字段，是否有@KevinAutowired注解，如果有需要维护依赖注入关系
        for (Map.Entry<String,Object> entry: ioc.entrySet()) {
            // 获取bean对象中的字段信息
            Field[] declaredFields = entry.getValue().getClass().getDeclaredFields();
            // 遍历判断处理
            for (int i = 0; i < declaredFields.length; i++) {
                Field declaredField = declaredFields[i];   //  @KevinAutowired  private IDemoService demoService;
                if (!declaredField.isAnnotationPresent(KevinAutowired.class)) {
                    continue;
                }
                // 有该注解
                KevinAutowired annotation = declaredField.getAnnotation(KevinAutowired.class);
                String beanName = annotation.value();  // 需要注入的bean的id
                if ("".equals(beanName.trim())) {
                    // 没有配置具体的bean id，那就需要根据当前字段类型注入（接口注入）  IDemoService
                    beanName = declaredField.getType().getName();
                }
                // 开启赋值
                declaredField.setAccessible(true);
                try {
                    declaredField.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return ;
        }
        for(Map.Entry<String,Object> entry: ioc.entrySet()) {
            // 获取ioc中当前遍历的对象的class类型
            Class<?> aClass = entry.getValue().getClass();
            if(!aClass.isAnnotationPresent(KevinController.class)) {
                continue;
            }
            String baseUrl = "";
            if(aClass.isAnnotationPresent(KevinRequestMapping.class)) {
                KevinRequestMapping annotation = aClass.getAnnotation(KevinRequestMapping.class);
                baseUrl = annotation.value(); // 等同于/demo
            }
            // 获取方法
            Method[] methods = aClass.getMethods();
            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];
                //  方法没有标识 KevinRequestMapping，就不处理
                if(!method.isAnnotationPresent(KevinRequestMapping.class)) {
                    continue;
                }
                // 如果标识，就处理
                KevinRequestMapping annotation = method.getAnnotation(KevinRequestMapping.class);
                String methodUrl = annotation.value();  // /query
                String url = baseUrl + methodUrl;    // 计算出来的url /demo/query
                // 把method所有信息及url封装为一个Handler
                Handler handler = new Handler(entry.getValue(), method, Pattern.compile(url));
                // 计算方法的参数位置信息  // query(HttpServletRequest request, HttpServletResponse response,String name)
                Parameter[] parameters = method.getParameters();
                for (int j = 0; j < parameters.length; j++) {
                    Parameter parameter = parameters[j];
                    if(parameter.getType() == HttpServletRequest.class || parameter.getType() == HttpServletResponse.class) {
                        // 如果是request和response对象，那么参数名称写HttpServletRequest和HttpServletResponse
                        handler.getParamIndexMapping().put(parameter.getType().getSimpleName(), j);
                    }else{
                        handler.getParamIndexMapping().put(parameter.getName(), j);  // <name,2>
                    }
                }
                // 建立url和method之间的映射关系（map缓存起来）
                handlerMapping.add(handler);
            }
        }
    }

    /**
     * 首字母小写方法
     */
    public String lowerFirst(String str) {
        char[] chars = str.toCharArray();
        if ('A' <= chars[0] && chars[0] <= 'Z') {
            chars[0] += 32;
        }
        return String.valueOf(chars);
    }

    private Handler getHandler(HttpServletRequest req) {
        // 为空则直接返回null
        if (handlerMapping.isEmpty()) {
            return null;
        }
        String url = req.getRequestURI();
        // 遍历handlerMapping
        for (Handler handler : handlerMapping) {
            // pattern封装了url，判断其是否匹配url
            Matcher matcher = handler.getPattern().matcher(url);
            // 不匹配则跳过
            if (!matcher.matches()) {
                continue;
            }
            // 匹配则返回handler
            return handler;
        }
        return null;
    }

}
