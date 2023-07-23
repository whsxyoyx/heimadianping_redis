package com.oyx.redis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan(basePackages = "com.oyx.redis.mapper")
@SpringBootApplication
public class Redis7HeimaDianpingMyselfApplication {

    public static void main(String[] args) {
        SpringApplication.run(Redis7HeimaDianpingMyselfApplication.class, args);
    }

}
