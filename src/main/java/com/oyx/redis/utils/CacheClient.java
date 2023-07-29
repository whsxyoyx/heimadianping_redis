package com.oyx.redis.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.oyx.redis.bean.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.oyx.redis.utils.RedisConstants.*;

/**
 * 封装Redis工具类
 * @author oyx
 * @create 2023-07-25 20:59
 */
@Slf4j
@Component
public class CacheClient {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 解决缓存穿透的set方法
     * @param key  传入的key
     * @param value  传入的value
     * @param time  设置有效时间
     * @param timeUnit  设置时间的单位
     */
    public  void set(String key, Object value, Long time, TimeUnit timeUnit){
        String val = JSONUtil.toJsonStr(value);
        redisTemplate.opsForValue().set(key,val,time,timeUnit);
    }

    /**
     * 解决缓存穿透的queryWithPassThrough方法
     * @param keyPrefix  key的前缀
     * @param id  id
     * @param type  要返回的类型
     * @param dbFallBack   数据查询语句
     * @param time  设置有效时间
     * @param timeUnit  设置时间的单位
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id,
                        Class<R> type, Function<ID,R> dbFallBack,
                        Long time, TimeUnit timeUnit){
        String key = keyPrefix+id;
        //1、从缓存中获取key
        String objJSON = redisTemplate.opsForValue().get(key);
        //2、判断是否存在
        if(StrUtil.isNotBlank(objJSON)){
            //2.1、存在返回
            return JSONUtil.toBean(objJSON,type);
        }
        //3、判断是否为""空字符串
        if(objJSON != null){
            //3.1、返回一个错误信息
            return null;
        }
        //4、不存在，查数据库
        R goalObj = dbFallBack.apply(id);
        //5、判断查出来的数据是否存在
        if(goalObj == null){
            //5.1、不存在，传空字符串到redis
            redisTemplate.opsForValue().set(key,"",time, timeUnit);
            //5.2、返回一个错误信息
            return null;
        }
        //6、数据库中存在数据
        this.set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(goalObj),time, timeUnit);
        //7、返回
        return goalObj;
    }

    public  void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        //写入redis
        redisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallBack,Long time,TimeUnit timeUnit){
        String key = keyPrefix + id;
        //1、从redis中查询
        String objJSON = redisTemplate.opsForValue().get(key);
        //2、判断缓存中是否有数据
        if(StrUtil.isBlank(objJSON)){
            //2.1、不存在，直接返回
            return null;
        }
        //3、命中缓存，获取缓存中相关数据
        //3.1、把objJSON反序列化为对象
        RedisData redisData = JSONUtil.toBean(objJSON, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        //3.2、获取redisData中存放的数据
        R shopObj = JSONUtil.toBean(jsonObject, type);
        //3.3、获取redisData中存放的expireTime过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //4、判断缓存是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //4.1、没过期，则直接返回
            return shopObj;
        }
        //5、缓存过期，进行缓存重建
        String lock = LOCK_SHOP_KEY + id;
        //5.1、尝试获取锁
        boolean isLock = tryLock(lock);
        if(isLock){
            //5.2、获取到了锁,开一个新的线程完成数据查询和缓存的操作
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R obj = dbFallBack.apply(id);
                    setWithLogicalExpire(key,obj,time,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //5.3、释放锁
                    unLock(lock);
                }
            });
        }
        return shopObj;

    }
    public boolean tryLock(String key){
        Boolean lock = redisTemplate.opsForValue().setIfAbsent(key, "lock锁", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(lock);
    }

    public void unLock(String key){
        redisTemplate.delete(key);
    }
}
