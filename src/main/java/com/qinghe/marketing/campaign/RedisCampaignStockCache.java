package com.qinghe.marketing.campaign;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.OptionalLong;

@Component
public class RedisCampaignStockCache implements CampaignStockCache {

    private static final DefaultRedisScript<Long> INITIALIZE = new DefaultRedisScript<Long>(
            "local current = redis.call('get', KEYS[1]); "
                    + "if not current then redis.call('set', KEYS[1], ARGV[1]); return 1; end; "
                    + "if current == ARGV[1] then return 0; end; return -1;", Long.class);
    private static final DefaultRedisScript<Long> INCREASE = new DefaultRedisScript<Long>(
            "local applied = redis.call('get', KEYS[2]); "
                    + "if applied then return tonumber(redis.call('get', KEYS[1]) or '-1'); end; "
                    + "if not redis.call('get', KEYS[1]) then return -1; end; "
                    + "local updated = redis.call('incrby', KEYS[1], ARGV[1]); "
                    + "redis.call('set', KEYS[2], '1'); return updated;", Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisCampaignStockCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void initialize(long campaignId, long initialStock) {
        Long result = redisTemplate.execute(INITIALIZE,
                Collections.singletonList(stockKey(campaignId)), String.valueOf(initialStock));
        if (result == null) {
            throw unavailable("Redis did not return campaign stock initialization result");
        }
        if (result < 0) {
            throw new QingheBusinessException(QingheErrorCode.BUSINESS_STATE_CONFLICT,
                    "campaign stock is already initialized with a different value");
        }
    }

    @Override
    public long applyApprovedIncrease(long campaignId, String adjustmentNo, long incrementStock) {
        Long result = redisTemplate.execute(INCREASE,
                Arrays.asList(stockKey(campaignId), adjustmentKey(adjustmentNo)),
                String.valueOf(incrementStock));
        if (result == null || result < 0) {
            throw unavailable("campaign stock is not initialized in Redis");
        }
        return result;
    }

    @Override
    public OptionalLong currentAvailableStock(long campaignId) {
        String value = redisTemplate.opsForValue().get(stockKey(campaignId));
        if (value == null) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(value));
        } catch (NumberFormatException invalid) {
            throw unavailable("campaign stock in Redis is invalid");
        }
    }

    private static String stockKey(long campaignId) {
        return "qh:campaign:stock:" + campaignId;
    }

    private static String adjustmentKey(String adjustmentNo) {
        return "qh:campaign:stock-adjustment:" + adjustmentNo;
    }

    private static QingheBusinessException unavailable(String message) {
        return new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE, message);
    }
}
