package com.qinghe.marketing.campaign;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCampaignStockCacheTest {

    @Test
    void shouldTreatSameInitializationAsIdempotentAndRejectConflict() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("1000")))
                .thenReturn(0L).thenReturn(-1L);
        RedisCampaignStockCache cache = new RedisCampaignStockCache(redis);

        cache.initialize(10L, 1000L);
        assertThrows(QingheBusinessException.class, () -> cache.initialize(10L, 1000L));
    }

    @Test
    void shouldUseAdjustmentMarkerForIdempotentIncreaseAndExposeCurrentValue() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), eq("200"))).thenReturn(1200L);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("qh:campaign:stock:10")).thenReturn("1200").thenReturn(null);
        RedisCampaignStockCache cache = new RedisCampaignStockCache(redis);

        assertEquals(1200L, cache.applyApprovedIncrease(10L, "IAD-1", 200L));
        assertEquals(1200L, cache.currentAvailableStock(10L).getAsLong());
        assertFalse(cache.currentAvailableStock(10L).isPresent());
        verify(redis).execute(any(RedisScript.class), anyList(), eq("200"));
    }
}
