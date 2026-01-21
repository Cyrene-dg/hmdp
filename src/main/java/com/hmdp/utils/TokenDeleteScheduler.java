package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class TokenDeleteScheduler {
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "token-delay-delete"));
    private final StringRedisTemplate stringRedisTemplate;

    public TokenDeleteScheduler(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void deleteWithDelay(String key, long delayMs) {
        executor.schedule(() -> stringRedisTemplate.delete(key), delayMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
