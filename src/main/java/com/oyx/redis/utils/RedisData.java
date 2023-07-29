package com.oyx.redis.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用于逻辑过期解决缓存击穿问题
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
