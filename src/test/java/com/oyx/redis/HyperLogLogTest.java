package com.oyx.redis;

/**
 * @author oyx
 * @create 2023-07-29 20:54
 */

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@SpringBootTest
public class HyperLogLogTest {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public  void test(){
        String[] arr = new String[1000];
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String key = "pv:"+time;
        int j = 0;
        for(int i=0;i<1000000;i++){
            j = i % 1000;
            arr[j] = "user:"+i;
            if(j == 999){
                redisTemplate.opsForHyperLogLog().add(key,arr);
            }
        }
        Long size = redisTemplate.opsForHyperLogLog().size(key);
        System.out.println(size);
    }
}
