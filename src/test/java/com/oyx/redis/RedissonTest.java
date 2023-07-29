package com.oyx.redis;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author oyx
 * @create 2023-07-27 11:14
 */
@SpringBootTest
@Slf4j
public class RedissonTest {
    @Autowired
    private RedissonClient redissonClient;

    private RLock lock;

    @BeforeEach
    public void setUp(){
        lock = redissonClient.getLock("order");
    }

    @Test
    public void  test1(){
        boolean isLock = lock.tryLock();
        if(!isLock){
            log.info("获取锁失败,1");
        }
        try {
            log.info("获取锁成功,1");
            test2();
            log.info("开始执行业务,1");
        } finally {
            log.info("释放锁,1");
            lock.unlock();
        }

    }

    public void  test2(){
        boolean isLock = lock.tryLock();
        if(!isLock){
            log.info("获取锁失败,2");
            return;
        }
        try {
            log.info("获取锁成功,2");
            log.info("开始执行业务,2");
        } finally {
            log.info("释放锁，2");
            lock.unlock();
        }

    }
}
