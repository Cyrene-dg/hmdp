package com.qinghe.marketing.identity;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class RedisPlatformSessionCache implements PlatformSessionCache {

    private static final String PREFIX = "qh:session:";
    private final StringRedisTemplate redisTemplate;
    private final BusinessClock clock;

    public RedisPlatformSessionCache(StringRedisTemplate redisTemplate, BusinessClock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Override
    public Optional<AuthenticatedMember> find(String accessTokenHash) {
        String key = key(accessTokenHash);
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        try {
            AuthenticatedMember member = new AuthenticatedMember(
                    Long.parseLong(String.valueOf(values.get("memberId"))),
                    Long.parseLong(String.valueOf(values.get("platformUserId"))),
                    String.valueOf(values.get("externalMemberNo")),
                    LocalDateTime.parse(String.valueOf(values.get("expiresAt"))));
            if (!member.expiresAt().isAfter(clock.dateTime())) {
                redisTemplate.delete(key);
                return Optional.empty();
            }
            return Optional.of(member);
        } catch (RuntimeException corruptEntry) {
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    @Override
    public void put(String accessTokenHash, AuthenticatedMember member) {
        Duration ttl = Duration.between(clock.dateTime(), member.expiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        String key = key(accessTokenHash);
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("memberId", String.valueOf(member.memberId()));
        values.put("platformUserId", String.valueOf(member.platformUserId()));
        values.put("externalMemberNo", member.externalMemberNo());
        values.put("expiresAt", member.expiresAt().toString());
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static String key(String accessTokenHash) {
        return PREFIX + accessTokenHash;
    }
}
