package com.oyx.redis.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author oyx
 * @create 2023-07-26 19:35
 */

public class SimpleRedisLock implements ILock{

    private String name;

    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static{
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeSeconds) {
        //1、获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //2、获取锁
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeSeconds, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }


    //TODO Lua脚本的方式执行释放锁的操作,以防在执行threadId.equals(id)判断的成功后，
    // 释放锁之前发生阻塞，出现超时释放，产生误删的情况
    @Override
    public void unLock() {
       /* //1、获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //2、判断标识是否一致
        //2.1、从redis取锁的标识
        String id = redisTemplate.opsForValue().get(KEY_PREFIX + name);
        if(threadId.equals(id)){
            //2.2、一致,释放锁
            redisTemplate.delete(KEY_PREFIX + name);
        }
        //2.3、不一致,不管*/
        String key = KEY_PREFIX + name;
        List<String> list = new ArrayList<>();
        list.add(key);
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        redisTemplate.execute(UNLOCK_SCRIPT,list,threadId);
    }

    /*@Override
    public void unLock() {
        //1、获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //2、判断标识是否一致
        //2.1、从redis取锁的标识
        String id = redisTemplate.opsForValue().get(KEY_PREFIX + name);
        if(threadId.equals(id)){
            //2.2、一致,释放锁
            redisTemplate.delete(KEY_PREFIX + name);
        }
        //2.3、不一致,不管
    }*/
}
