package com.zify.common.util;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 * <p>
 * 封装常用的 get / set / delete / expire 操作。
 * 仅在 Redis 连接可用时注册为 Bean。
 */
@Component
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ── Get ───────────────────────────────────────────────

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // ── Set ───────────────────────────────────────────────

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    // ── Delete ────────────────────────────────────────────

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    // ── Expire ────────────────────────────────────────────

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }
}
