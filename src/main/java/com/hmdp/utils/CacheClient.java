package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrow(String keyPrefix, ID id, Class<R> clazz,
                                        Function<ID, R> dbFallBack, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, clazz);
        }
        if (json != null) {
            return null;
        }

        R r = dbFallBack.apply(id);
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, timeUnit);
        return r;
    }

    public <R, ID> R queryWithLogicExpire(String keyPrefix, String lockPrefix, ID id, Class<R> clazz,
                                          Function<ID, R> dbFallBack, Long logicTime, TimeUnit timeUnit)
            throws InterruptedException {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(json)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, clazz);

        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        String lockKey = lockPrefix + id;
        boolean lock = getLock(lockKey);
        if (lock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R latest = dbFallBack.apply(id);
                    setWithLogicExpire(key, latest, logicTime, timeUnit);
                } catch (Exception e) {
                    log.error("Rebuild cache failed, key={}", key, e);
                } finally {
                    releaseLock(lockKey);
                }
            });
        }

        return r;
    }

    private boolean getLock(String key) {
        Boolean set = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "", RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(set);
    }

    private boolean releaseLock(String key) {
        Boolean deleted = stringRedisTemplate.delete(key);
        return BooleanUtil.isTrue(deleted);
    }
}