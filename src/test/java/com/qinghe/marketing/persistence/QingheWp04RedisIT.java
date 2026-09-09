package com.qinghe.marketing.persistence;

import com.qinghe.marketing.claim.ClaimReservationCommand;
import com.qinghe.marketing.claim.ClaimReservationResult;
import com.qinghe.marketing.claim.RedisClaimReservationStore;
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

/** Real Redis WP-04 probe. All keys use a random campaign id and are removed precisely. */
class QingheWp04RedisIT {

    private static final String HOST = System.getProperty("qinghe.it.redis.host", "127.0.0.1");
    private static final int PORT = Integer.getInteger("qinghe.it.redis.port", 6379);
    private static final String PASSWORD = credential("qinghe.it.redis.password", "QINGHE_IT_REDIS_PASSWORD");

    @Test
    void shouldReserveReplayRejectConflictAndCompensateExactlyOnce() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(HOST, PORT);
        if (!PASSWORD.isEmpty()) {
            configuration.setPassword(RedisPassword.of(PASSWORD));
        }
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();

        long campaignId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900000000L) + 100000000L;
        long memberId = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 900000000L) + 100000000L;
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        String reservationId = "RSV-" + suffix;
        String requestId = "REQ-" + suffix;
        List<String> cleanup = new ArrayList<String>();
        cleanup.add("qh:campaign:stock:" + campaignId);
        cleanup.add("qh:claim:request:" + campaignId + ":" + memberId + ":" + requestId);
        cleanup.add("qh:claim:request:" + campaignId + ":" + memberId + ":REQ2-" + suffix);
        cleanup.add("qh:claim:member:" + campaignId + ":" + memberId);
        cleanup.add("qh:claim:reservation:" + campaignId + ":" + reservationId);
        cleanup.add("qh:claim:reservation:" + campaignId + ":RSV2-" + suffix);
        cleanup.add("qh:claim:reservation:pending:" + campaignId);
        try {
            redis.opsForValue().set("qh:campaign:stock:" + campaignId, "2");
            RedisClaimReservationStore store = new RedisClaimReservationStore(redis);
            LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);
            ClaimReservationCommand firstCommand = command(campaignId, memberId, requestId,
                    "digest-a", reservationId, "CLM-" + suffix, "EVT-" + suffix, now);

            ClaimReservationResult first = store.reserve(firstCommand);
            ClaimReservationResult replay = store.reserve(firstCommand);
            ClaimReservationResult conflict = store.reserve(command(campaignId, memberId, requestId,
                    "digest-b", "RSVX-" + suffix, "CLMX-" + suffix, "EVTX-" + suffix, now));
            ClaimReservationResult memberDuplicate = store.reserve(command(campaignId, memberId,
                    "REQ2-" + suffix, "digest-c", "RSV2-" + suffix,
                    "CLM2-" + suffix, "EVT2-" + suffix, now));

            assertEquals(ClaimReservationResult.Outcome.RESERVED, first.outcome());
            assertEquals(ClaimReservationResult.Outcome.IDEMPOTENT_REPLAY, replay.outcome());
            assertEquals(reservationId, replay.reservationId());
            assertEquals(ClaimReservationResult.Outcome.REQUEST_CONFLICT, conflict.outcome());
            assertEquals(ClaimReservationResult.Outcome.MEMBER_ALREADY_RESERVED, memberDuplicate.outcome());
            assertEquals("1", redis.opsForValue().get("qh:campaign:stock:" + campaignId));
            assertEquals(1, store.findPendingBefore(campaignId, now.plusSeconds(1), 10).size());

            store.compensate(campaignId, memberId, requestId, reservationId, "TEST_ROLLBACK");
            store.compensate(campaignId, memberId, requestId, reservationId, "TEST_ROLLBACK");
            assertEquals("2", redis.opsForValue().get("qh:campaign:stock:" + campaignId));
            assertEquals(0, store.findPendingBefore(campaignId, now.plusSeconds(1), 10).size());

            ClaimReservationResult afterCompensation = store.reserve(command(campaignId, memberId,
                    requestId, "digest-a", "RSV2-" + suffix,
                    "CLM2-" + suffix, "EVT2-" + suffix, now));
            assertEquals(ClaimReservationResult.Outcome.RESERVED, afterCompensation.outcome());
            store.markPersisted(campaignId, afterCompensation.reservationId());
            assertEquals("PERSISTED", redis.opsForHash().get(
                    "qh:claim:reservation:" + campaignId + ":RSV2-" + suffix, "state"));
            assertEquals(0L, redis.opsForZSet().size("qh:claim:reservation:pending:" + campaignId));
        } finally {
            redis.delete(cleanup);
            connectionFactory.destroy();
        }
    }

    private static ClaimReservationCommand command(long campaignId, long memberId, String requestId,
                                                    String digest, String reservationId,
                                                    String claimNo, String eventId, LocalDateTime now) {
        return new ClaimReservationCommand(campaignId, memberId, requestId, digest,
                reservationId, claimNo, eventId, now, now.minusMinutes(1), now.plusDays(1));
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
