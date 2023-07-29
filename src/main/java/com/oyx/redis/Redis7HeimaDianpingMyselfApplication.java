package com.oyx.redis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan(basePackages = "com.oyx.redis.mapper")
@SpringBootApplication
//暴露代理对象
@EnableAspectJAutoProxy(exposeProxy = true)
public class Redis7HeimaDianpingMyselfApplication {

    public static void main(String[] args) {
        SpringApplication.run(Redis7HeimaDianpingMyselfApplication.class, args);
    }

}
