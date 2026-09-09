package com.qinghe.marketing.persistence;

import com.qinghe.marketing.claim.ClaimReservationCommand;
import com.qinghe.marketing.claim.RedisClaimReservationStore;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Real Redis WP-05 probe for persisted compensation and issued reservation finality. */
class QingheWp05RedisIT {

    private static final String HOST = System.getProperty("qinghe.it.redis.host", "127.0.0.1");
    private static final int PORT = Integer.getInteger("qinghe.it.redis.port", 6379);
    private static final String PASSWORD = credential("qinghe.it.redis.password", "QINGHE_IT_REDIS_PASSWORD");

    @Test
    void shouldCompensatePersistedReservationOnceButNeverCompensateIssuedRight() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(HOST, PORT);
        if (!PASSWORD.isEmpty()) configuration.setPassword(RedisPassword.of(PASSWORD));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();

        long campaignId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900000000L) + 100000000L;
        long memberOne = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 800000000L) + 100000000L;
        long memberTwo = memberOne + 1;
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        List<String> cleanup = new ArrayList<String>();
        cleanup.add("qh:campaign:stock:" + campaignId);
        cleanup.add("qh:claim:request:" + campaignId + ":" + memberOne + ":REQ-A-" + suffix);
        cleanup.add("qh:claim:request:" + campaignId + ":" + memberTwo + ":REQ-B-" + suffix);
        cleanup.add("qh:claim:member:" + campaignId + ":" + memberOne);
        cleanup.add("qh:claim:member:" + campaignId + ":" + memberTwo);
        cleanup.add("qh:claim:reservation:" + campaignId + ":RSV-A-" + suffix);
        cleanup.add("qh:claim:reservation:" + campaignId + ":RSV-B-" + suffix);
        cleanup.add("qh:claim:reservation:pending:" + campaignId);
        try {
            redis.opsForValue().set("qh:campaign:stock:" + campaignId, "2");
            RedisClaimReservationStore store = new RedisClaimReservationStore(redis);
            LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);

            ClaimReservationCommand failed = command(campaignId, memberOne, "REQ-A-" + suffix,
                    "RSV-A-" + suffix, "CLM-A-" + suffix, "EVT-A-" + suffix, now);
            store.reserve(failed);
            store.markPersisted(campaignId, failed.reservationId());
            store.compensate(campaignId, memberOne, failed.requestId(), failed.reservationId(),
                    "ISSUE_RETRY_EXHAUSTED");
            store.compensate(campaignId, memberOne, failed.requestId(), failed.reservationId(),
                    "ISSUE_RETRY_EXHAUSTED");
            assertEquals("2", redis.opsForValue().get("qh:campaign:stock:" + campaignId));
            assertEquals("COMPENSATED", redis.opsForHash().get(
                    "qh:claim:reservation:" + campaignId + ":" + failed.reservationId(), "state"));

            ClaimReservationCommand issued = command(campaignId, memberTwo, "REQ-B-" + suffix,
                    "RSV-B-" + suffix, "CLM-B-" + suffix, "EVT-B-" + suffix, now);
            store.reserve(issued);
            store.markPersisted(campaignId, issued.reservationId());
            store.markIssued(campaignId, issued.reservationId(), "ENT-B-" + suffix);
            store.markIssued(campaignId, issued.reservationId(), "ENT-B-" + suffix);
            assertThrows(QingheBusinessException.class, () -> store.compensate(
                    campaignId, memberTwo, issued.requestId(), issued.reservationId(), "LATE_FAILURE"));
            assertEquals("1", redis.opsForValue().get("qh:campaign:stock:" + campaignId));
            assertEquals("ISSUED", redis.opsForHash().get(
                    "qh:claim:reservation:" + campaignId + ":" + issued.reservationId(), "state"));
        } finally {
            redis.delete(cleanup);
            connectionFactory.destroy();
        }
    }

    private static ClaimReservationCommand command(long campaignId, long memberId, String requestId,
                                                    String reservationId, String claimNo,
                                                    String eventId, LocalDateTime now) {
        return new ClaimReservationCommand(campaignId, memberId, requestId, "digest-" + requestId,
                reservationId, claimNo, eventId, now, now.minusMinutes(1), now.plusDays(1));
    }

    private static String credential(String propertyName, String environmentName) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) return propertyValue;
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null ? "" : environmentValue;
    }
}
