package com.oyx.redis.utils;


/**
 * @author oyx
 * @create 2023-07-26 19:32
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeSeconds 锁持有的超时时间，过期后自动失效
     * @return true 代表获取锁成功 ,false 代表获取锁失败
     */
    public boolean tryLock(long timeSeconds);

    /**
     * 释放锁
     */
    public void unLock();
}
