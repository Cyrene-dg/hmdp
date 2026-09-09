package com.qinghe.marketing.claim;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RedisClaimReservationStore implements ClaimReservationStore {

    private static final DefaultRedisScript<List> RESERVE_SCRIPT = reserveScript();
    private static final DefaultRedisScript<Long> MARK_PERSISTED = new DefaultRedisScript<Long>(
            "if redis.call('hget', KEYS[1], 'state') == 'RESERVED' then "
                    + "redis.call('hset', KEYS[1], 'state', 'PERSISTED'); "
                    + "redis.call('zrem', KEYS[2], ARGV[1]); return 1; end; return 0;", Long.class);
    private static final DefaultRedisScript<Long> COMPENSATE = new DefaultRedisScript<Long>(
            "local state = redis.call('hget', KEYS[4], 'state'); "
                    + "if state == 'COMPENSATED' then return 0; end; "
                    + "if state ~= 'RESERVED' then return -1; end; "
                    + "redis.call('incr', KEYS[1]); "
                    + "if redis.call('get', KEYS[2]) == ARGV[1] then redis.call('del', KEYS[2]); end; "
                    + "redis.call('hset', KEYS[3], 'state', 'COMPENSATED'); "
                    + "redis.call('hset', KEYS[4], 'state', 'COMPENSATED', 'reason', ARGV[2]); "
                    + "redis.call('zrem', KEYS[5], ARGV[1]); return 1;", Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisClaimReservationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ClaimReservationResult reserve(ClaimReservationCommand command) {
        long nowMillis = epochMillis(command.now());
        long beginMillis = epochMillis(command.claimBeginAt());
        long endMillis = epochMillis(command.claimEndAt());
        long ttlSeconds = Math.max(Duration.ofDays(1).getSeconds(),
                Duration.between(command.now(), command.claimEndAt().plusDays(7)).getSeconds());
        List<?> raw = redisTemplate.execute(RESERVE_SCRIPT, Arrays.asList(
                        stockKey(command.campaignId()), requestKey(command), memberKey(command),
                        reservationKey(command.campaignId(), command.reservationId()),
                        pendingKey(command.campaignId())),
                command.requestDigest(), command.reservationId(), command.claimNo(), command.eventId(),
                String.valueOf(nowMillis), String.valueOf(beginMillis), String.valueOf(endMillis),
                String.valueOf(ttlSeconds), String.valueOf(command.campaignId()),
                String.valueOf(command.memberId()), command.requestId());
        if (raw == null || raw.isEmpty()) {
            throw unavailable("Redis did not return a claim reservation result");
        }
        int code = Integer.parseInt(String.valueOf(raw.get(0)));
        return new ClaimReservationResult(outcome(code), value(raw, 1), value(raw, 2), value(raw, 3));
    }

    @Override
    public void markPersisted(long campaignId, String reservationId) {
        redisTemplate.execute(MARK_PERSISTED,
                Arrays.asList(reservationKey(campaignId, reservationId), pendingKey(campaignId)),
                reservationId);
    }

    @Override
    public void compensate(long campaignId, long memberId, String requestId,
                           String reservationId, String reason) {
        Long result = redisTemplate.execute(COMPENSATE, Arrays.asList(
                        stockKey(campaignId), memberKey(campaignId, memberId),
                        requestKey(campaignId, memberId, requestId),
                        reservationKey(campaignId, reservationId), pendingKey(campaignId)),
                reservationId, reason == null ? "PERSISTENCE_FAILED" : reason);
        if (result == null || result < 0) {
            throw unavailable("claim reservation cannot be compensated from its current state");
        }
    }

    @Override
    public List<ClaimReservationSnapshot> findPendingBefore(long campaignId,
                                                             LocalDateTime cutoff, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        Set<String> reservationIds = redisTemplate.opsForZSet().rangeByScore(
                pendingKey(campaignId), Double.NEGATIVE_INFINITY, epochMillis(cutoff), 0, limit);
        if (reservationIds == null || reservationIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ClaimReservationSnapshot> pending = new ArrayList<ClaimReservationSnapshot>();
        for (String reservationId : reservationIds) {
            String key = reservationKey(campaignId, reservationId);
            Map<Object, Object> values = redisTemplate.opsForHash().entries(key);
            if (values.isEmpty()) {
                redisTemplate.opsForZSet().remove(pendingKey(campaignId), reservationId);
                continue;
            }
            if (!"RESERVED".equals(String.valueOf(values.get("state")))) {
                redisTemplate.opsForZSet().remove(pendingKey(campaignId), reservationId);
                continue;
            }
            pending.add(new ClaimReservationSnapshot(
                    Long.parseLong(String.valueOf(values.get("campaignId"))),
                    Long.parseLong(String.valueOf(values.get("memberId"))),
                    String.valueOf(values.get("requestId")), reservationId,
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(
                            Long.parseLong(String.valueOf(values.get("reservedAt")))),
                            BusinessClock.BUSINESS_ZONE)));
        }
        return pending;
    }

    private static DefaultRedisScript<List> reserveScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<List>();
        script.setLocation(new ClassPathResource("qinghe_claim_reserve.lua"));
        script.setResultType(List.class);
        return script;
    }

    private static ClaimReservationResult.Outcome outcome(int code) {
        switch (code) {
            case 0: return ClaimReservationResult.Outcome.RESERVED;
            case 1: return ClaimReservationResult.Outcome.IDEMPOTENT_REPLAY;
            case 2: return ClaimReservationResult.Outcome.REQUEST_CONFLICT;
            case 3: return ClaimReservationResult.Outcome.MEMBER_ALREADY_RESERVED;
            case 4: return ClaimReservationResult.Outcome.SOLD_OUT;
            case 5: return ClaimReservationResult.Outcome.STOCK_NOT_INITIALIZED;
            case 6: return ClaimReservationResult.Outcome.CAMPAIGN_NOT_ACTIVE;
            default: throw unavailable("Redis returned an unknown claim reservation result");
        }
    }

    private static String value(List<?> values, int index) {
        return index < values.size() ? String.valueOf(values.get(index)) : "";
    }

    private static long epochMillis(java.time.LocalDateTime value) {
        ZoneId zone = BusinessClock.BUSINESS_ZONE;
        return value.atZone(zone).toInstant().toEpochMilli();
    }

    private static String stockKey(long campaignId) {
        return "qh:campaign:stock:" + campaignId;
    }

    private static String requestKey(ClaimReservationCommand command) {
        return requestKey(command.campaignId(), command.memberId(), command.requestId());
    }

    private static String requestKey(long campaignId, long memberId, String requestId) {
        return "qh:claim:request:" + campaignId + ":" + memberId + ":" + requestId;
    }

    private static String memberKey(ClaimReservationCommand command) {
        return memberKey(command.campaignId(), command.memberId());
    }

    private static String memberKey(long campaignId, long memberId) {
        return "qh:claim:member:" + campaignId + ":" + memberId;
    }

    private static String reservationKey(long campaignId, String reservationId) {
        return "qh:claim:reservation:" + campaignId + ":" + reservationId;
    }

    private static String pendingKey(long campaignId) {
        return "qh:claim:reservation:pending:" + campaignId;
    }

    private static QingheBusinessException unavailable(String message) {
        return new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE, message);
    }
}
