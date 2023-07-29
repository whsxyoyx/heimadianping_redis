package com.oyx.redis.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author oyx
 * @create 2023-07-26 10:02
 */
//redis的id全局生成器
@Component
public class RedisIdWorker {
    /**
     * 指定开始的时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1672531200L;
    /**
     * 序列号位数
     */
    private static final long COUNT_BITS = 32;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        //2.1 获取当前时间，精确到天
        String data = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.1 自增长
        long increment = redisTemplate.opsForValue().increment("icr:"+keyPrefix + ":" + data);

        //3.拼接并返回
        return timeStamp << COUNT_BITS | increment;
    }

    /*public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2023, 1, 1, 0, 0, 0);
        long l = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(l);
    }*/
}
