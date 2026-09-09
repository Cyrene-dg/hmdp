package com.qinghe.marketing.persistence;

import com.qinghe.marketing.campaign.RedisCampaignStockCache;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Real Redis WP-03 probe. It creates and removes two uniquely named keys. */
class QingheWp03RedisIT {

    private static final String HOST = System.getProperty("qinghe.it.redis.host", "127.0.0.1");
    private static final int PORT = Integer.getInteger("qinghe.it.redis.port", 6379);
    private static final String PASSWORD = credential("qinghe.it.redis.password", "QINGHE_IT_REDIS_PASSWORD");

    @Test
    void shouldInitializeAndApplyOneApprovedIncreaseIdempotently() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(HOST, PORT);
        if (!PASSWORD.isEmpty()) {
            configuration.setPassword(RedisPassword.of(PASSWORD));
        }
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();

        long campaignId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900000000L) + 100000000L;
        String adjustmentNo = "IAD-IT-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        String stockKey = "qh:campaign:stock:" + campaignId;
        String adjustmentKey = "qh:campaign:stock-adjustment:" + adjustmentNo;
        try {
            RedisCampaignStockCache cache = new RedisCampaignStockCache(redis);
            cache.initialize(campaignId, 1000L);
            cache.initialize(campaignId, 1000L);
            assertThrows(QingheBusinessException.class, () -> cache.initialize(campaignId, 999L));

            assertEquals(1200L, cache.applyApprovedIncrease(campaignId, adjustmentNo, 200L));
            assertEquals(1200L, cache.applyApprovedIncrease(campaignId, adjustmentNo, 200L));
            assertEquals(1200L, cache.currentAvailableStock(campaignId).getAsLong());
        } finally {
            redis.delete(Arrays.asList(stockKey, adjustmentKey));
            connectionFactory.destroy();
        }
    }

    private static String credential(String propertyName, String environmentName) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) {
            return propertyValue;
        }
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null ? "" : environmentValue;
    }
}
