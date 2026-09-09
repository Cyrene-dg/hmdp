package com.qinghe.marketing.identity;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPlatformSessionCacheTest {

    private static final Instant NOW = Instant.parse("2026-09-08T07:00:00Z");
    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private final BusinessClock clock = () -> NOW;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldStoreOnlyHashedKeyAndExpireAtDatabaseSessionBoundary() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        RedisPlatformSessionCache cache = new RedisPlatformSessionCache(redis, clock);
        LocalDateTime expiresAt = clock.dateTime().plusMinutes(30);

        cache.put(HASH_A, new AuthenticatedMember(1L, 86L, "M100086", expiresAt));

        ArgumentCaptor<Map> values = ArgumentCaptor.forClass(Map.class);
        verify(hashes).putAll(eq("qh:session:" + HASH_A), values.capture());
        assertFalse(values.getValue().containsValue("raw-platform-token"));
        verify(redis).expire("qh:session:" + HASH_A, 1_800_000L, TimeUnit.MILLISECONDS);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldReadValidEntryAndDiscardCorruptEntry() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        String key = "qh:session:" + HASH_B;
        Map<Object, Object> values = new LinkedHashMap<Object, Object>();
        values.put("memberId", "1");
        values.put("platformUserId", "86");
        values.put("externalMemberNo", "M100086");
        values.put("expiresAt", clock.dateTime().plusMinutes(5).toString());
        when(hashes.entries(key)).thenReturn(values);
        RedisPlatformSessionCache cache = new RedisPlatformSessionCache(redis, clock);

        Optional<AuthenticatedMember> member = cache.find(HASH_B);
        assertEquals("M100086", member.orElseThrow(IllegalStateException::new).externalMemberNo());

        values.put("memberId", "not-a-number");
        assertFalse(cache.find(HASH_B).isPresent());
        verify(redis).delete(key);
    }
}
