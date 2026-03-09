package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RabbitMqConstants;
import com.hmdp.utils.RedisIdWoker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT;
    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT;

    private static final String PENDING_ORDER_KEY_PREFIX = "seckill:pending:order:";
    private static final String PENDING_ORDER_RETRY_KEY_PREFIX = "seckill:pending:retry:";
    private static final String PENDING_ORDER_RETURNED_KEY_PREFIX = "seckill:pending:returned:";
    private static final String PENDING_ORDER_SCHEDULE_KEY = "seckill:pending:order:schedule";
    private static final String DEAD_ORDER_KEY_PREFIX = "seckill:dead:order:";
    private static final String DLQ_RETRY_KEY_PREFIX = "seckill:dlq:retry:";

    private static final long PENDING_ORDER_TTL_SECONDS = 3600L;
    private static final long DEAD_ORDER_TTL_SECONDS = 7 * 24 * 3600L;
    private static final int MAX_PENDING_RETRY = 8;
    private static final int MAX_DLQ_RETRY = 3;
    private static final int PENDING_RETRY_BATCH_SIZE = 200;
    private static final long PENDING_RETRY_BASE_DELAY_MS = 1_000L;
    private static final long PENDING_RETRY_MAX_DELAY_MS = 60_000L;

    private static final String REQUEST_IDEMPOTENCY_KEY_PREFIX = "seckill:idem:req:";
    private static final long REQUEST_IDEMPOTENCY_TTL_SECONDS = 30 * 60L;
    private static final String IDEM_STATUS_PROCESSING = "PROCESSING";
    private static final String IDEM_STATUS_SUCCESS = "SUCCESS";
    private static final String IDEM_STATUS_FAILED = "FAILED";
    private static final String IDEM_PROCESSING_MSG = "Request is processing, please retry with same X-Request-Id";

    private static final String RATE_LIMIT_GLOBAL_KEY = "rate:limit:seckill:global";
    private static final String RATE_LIMIT_VOUCHER_KEY_PREFIX = "rate:limit:seckill:voucher:";
    private static final String RATE_LIMIT_USER_KEY_PREFIX = "rate:limit:seckill:user:";

    private static final int GLOBAL_BUCKET_CAPACITY = 300;
    private static final double GLOBAL_BUCKET_REFILL_RATE = 120D;
    private static final int VOUCHER_BUCKET_CAPACITY = 120;
    private static final double VOUCHER_BUCKET_REFILL_RATE = 60D;
    private static final int USER_BUCKET_CAPACITY = 5;
    private static final double USER_BUCKET_REFILL_RATE = 1D;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>();
        TOKEN_BUCKET_SCRIPT.setLocation(new ClassPathResource("token_bucket.lua"));
        TOKEN_BUCKET_SCRIPT.setResultType(Long.class);

        COMPENSATE_SCRIPT = new DefaultRedisScript<>();
        COMPENSATE_SCRIPT.setLocation(new ClassPathResource("compensation.lua"));
        COMPENSATE_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    private RedisIdWoker redisIdWoker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void initRabbitCallbacks() {
        rabbitTemplate.setConfirmCallback(this::onPublisherConfirm);
        rabbitTemplate.setReturnCallback(this::onPublisherReturned);
    }

    @Override
    public Result secKillVoucher(Long voucherId) {
        return secKillVoucher(voucherId, null);
    }

    @Override
    public Result secKillVoucher(Long voucherId, String requestId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("Unauthorized");
        }
        Long userId = UserHolder.getUser().getId();
        return executeWithRequestIdempotency(userId, voucherId, requestId, () -> secKillVoucherCore(voucherId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result secKillVoucherSync(Long voucherId) {
        return secKillVoucherSync(voucherId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result secKillVoucherSync(Long voucherId, String requestId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("Unauthorized");
        }
        Long userId = UserHolder.getUser().getId();
        return executeWithRequestIdempotency(userId, voucherId, requestId, () -> secKillVoucherSyncCore(voucherId, userId));
    }

    private Result secKillVoucherCore(Long voucherId, Long userId) {
        if (!passRateLimit(userId, voucherId)) {
            return Result.fail("Too many requests, please try again later");
        }

        long orderId = redisIdWoker.nexId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        if (result == null) {
            return Result.fail("Order failed, please retry");
        }

        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "Stock not enough" : "Duplicate order is not allowed");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        cachePendingOrder(voucherOrder);
        publishOrderWithPendingRetry(voucherOrder, "initial_submit");
        return Result.ok(orderId);
    }

    private Result secKillVoucherSyncCore(Long voucherId, Long userId) {
        if (!passRateLimit(userId, voucherId)) {
            return Result.fail("Too many requests, please try again later");
        }

        long orderId = redisIdWoker.nexId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        if (result == null) {
            return Result.fail("Order failed, please retry");
        }

        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "Stock not enough" : "Duplicate order is not allowed");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        processVoucherOrder(voucherOrder);
        return Result.ok(orderId);
    }

    private Result executeWithRequestIdempotency(Long userId, Long voucherId, String requestId, Supplier<Result> supplier) {
        if (StrUtil.isBlank(requestId)) {
            return supplier.get();
        }

        String normalizedRequestId = StrUtil.trim(requestId);
        String key = requestIdempotencyKey(userId, voucherId, normalizedRequestId);
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                key,
                buildIdempotencyRecord(IDEM_STATUS_PROCESSING, null, null),
                REQUEST_IDEMPOTENCY_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        if (Boolean.TRUE.equals(locked)) {
            try {
                Result result = supplier.get();
                persistIdempotencyResult(key, result);
                return result;
            } catch (Exception e) {
                persistIdempotencyFailure(key, "Request failed, please retry later");
                throw e;
            }
        }

        return replayIdempotencyResult(key);
    }

    private void persistIdempotencyResult(String key, Result result) {
        if (result == null) {
            persistIdempotencyFailure(key, "Request failed, please retry later");
            return;
        }

        if (Boolean.TRUE.equals(result.getSuccess())) {
            Long orderId = parseOrderId(result.getData() == null ? null : result.getData().toString());
            String payload = buildIdempotencyRecord(IDEM_STATUS_SUCCESS, orderId, null);
            stringRedisTemplate.opsForValue().set(key, payload, REQUEST_IDEMPOTENCY_TTL_SECONDS, TimeUnit.SECONDS);
            return;
        }

        persistIdempotencyFailure(key, result.getErrorMsg());
    }

    private void persistIdempotencyFailure(String key, String errorMsg) {
        String payload = buildIdempotencyRecord(
                IDEM_STATUS_FAILED,
                null,
                StrUtil.isBlank(errorMsg) ? "Request failed" : errorMsg
        );
        stringRedisTemplate.opsForValue().set(key, payload, REQUEST_IDEMPOTENCY_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private Result replayIdempotencyResult(String key) {
        String payload = null;
        for (int i = 0; i < 3; i++) {
            payload = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isBlank(payload)) {
                return Result.fail(IDEM_PROCESSING_MSG);
            }

            JSONObject obj;
            try {
                obj = JSONUtil.parseObj(payload);
            } catch (Exception e) {
                return Result.fail(IDEM_PROCESSING_MSG);
            }

            String status = obj.getStr("status");
            if (IDEM_STATUS_SUCCESS.equals(status)) {
                Long orderId = obj.getLong("orderId");
                return orderId == null ? Result.ok() : Result.ok(orderId);
            }
            if (IDEM_STATUS_FAILED.equals(status)) {
                String errorMsg = obj.getStr("errorMsg");
                return Result.fail(StrUtil.isBlank(errorMsg) ? "Request failed" : errorMsg);
            }

            try {
                Thread.sleep(60L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.fail(IDEM_PROCESSING_MSG);
            }
        }
        return Result.fail(IDEM_PROCESSING_MSG);
    }

    private String buildIdempotencyRecord(String status, Long orderId, String errorMsg) {
        Map<String, Object> map = new HashMap<>(4);
        map.put("status", status);
        map.put("orderId", orderId);
        map.put("errorMsg", errorMsg);
        map.put("time", System.currentTimeMillis());
        return JSONUtil.toJsonStr(map);
    }

    private String requestIdempotencyKey(Long userId, Long voucherId, String requestId) {
        return REQUEST_IDEMPOTENCY_KEY_PREFIX + userId + ":" + voucherId + ":" + requestId;
    }

    @RabbitListener(queues = RabbitMqConstants.SECKILL_ORDER_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void consumeVoucherOrder(VoucherOrder voucherOrder) {
        try {
            processVoucherOrder(voucherOrder);
        } catch (Exception e) {
            log.error("Failed to consume seckill order, route to DLQ, order={}", voucherOrder, e);
            throw new RuntimeException(e);
        }
    }

    @RabbitListener(queues = RabbitMqConstants.SECKILL_ORDER_DLX_QUEUE)
    public void consumeDeadLetterOrder(VoucherOrder voucherOrder) {
        if (voucherOrder == null || voucherOrder.getId() == null) {
            log.error("Received invalid dead-letter order message: {}", voucherOrder);
            return;
        }

        long dlqRetry = incrementDlqRetry(voucherOrder.getId());
        if (dlqRetry <= MAX_DLQ_RETRY) {
            publishOrderWithPendingRetry(voucherOrder, "dlq_retry_" + dlqRetry);
            return;
        }

        markDeadOrder(voucherOrder, "DLQ retries exhausted: " + dlqRetry);
        compensateReservation(voucherOrder, "DLQ retries exhausted");
        clearDlqRetry(voucherOrder.getId());
    }

    @Scheduled(fixedDelay = 1000L)
    public void retryPendingOrders() {
        long now = System.currentTimeMillis();
        Set<String> dueOrderIds = stringRedisTemplate.opsForZSet()
                .rangeByScore(PENDING_ORDER_SCHEDULE_KEY, 0, now, 0, PENDING_RETRY_BATCH_SIZE);
        if (dueOrderIds == null || dueOrderIds.isEmpty()) {
            return;
        }

        for (String orderIdStr : dueOrderIds) {
            Long orderId = parseOrderId(orderIdStr);
            if (orderId == null) {
                stringRedisTemplate.opsForZSet().remove(PENDING_ORDER_SCHEDULE_KEY, orderIdStr);
                continue;
            }

            Long removed = stringRedisTemplate.opsForZSet().remove(PENDING_ORDER_SCHEDULE_KEY, orderIdStr);
            if (removed == null || removed <= 0) {
                continue;
            }

            VoucherOrder voucherOrder = getPendingOrder(orderId);
            if (voucherOrder == null) {
                clearPendingOrder(orderId);
                continue;
            }

            publishOrderWithPendingRetry(voucherOrder, "scheduled_retry");
        }
    }

    private void processVoucherOrder(VoucherOrder voucherOrder) {
        if (voucherOrder == null || voucherOrder.getId() == null || voucherOrder.getUserId() == null || voucherOrder.getVoucherId() == null) {
            log.warn("Incomplete seckill order payload, skip: {}", voucherOrder);
            return;
        }

        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        VoucherOrder existedOrder = query()
                .select("id")
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .last("LIMIT 1")
                .one();
        if (existedOrder != null) {
            clearDlqRetry(voucherOrder.getId());
            return;
        }

        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!update) {
            log.warn("Stock update failed, voucherId={}, orderId={}", voucherId, voucherOrder.getId());
            compensateReservation(voucherOrder, "DB stock update failed");
            clearDlqRetry(voucherOrder.getId());
            return;
        }

        try {
            boolean saved = save(voucherOrder);
            if (!saved) {
                throw new IllegalStateException("Failed to save order, orderId=" + voucherOrder.getId());
            }
        } catch (DuplicateKeyException e) {
            log.warn("Duplicate DB insert detected, treat as idempotent success, orderId={}", voucherOrder.getId());
        }

        clearDlqRetry(voucherOrder.getId());
    }

    private boolean passRateLimit(Long userId, Long voucherId) {
        long now = System.currentTimeMillis();

        boolean userAllowed = tryAcquireToken(
                RATE_LIMIT_USER_KEY_PREFIX + userId,
                USER_BUCKET_CAPACITY,
                USER_BUCKET_REFILL_RATE,
                now
        );
        if (!userAllowed) {
            return false;
        }

        boolean voucherAllowed = tryAcquireToken(
                RATE_LIMIT_VOUCHER_KEY_PREFIX + voucherId,
                VOUCHER_BUCKET_CAPACITY,
                VOUCHER_BUCKET_REFILL_RATE,
                now
        );
        if (!voucherAllowed) {
            return false;
        }

        return tryAcquireToken(
                RATE_LIMIT_GLOBAL_KEY,
                GLOBAL_BUCKET_CAPACITY,
                GLOBAL_BUCKET_REFILL_RATE,
                now
        );
    }

    private boolean tryAcquireToken(String key, int capacity, double refillRate, long nowMillis) {
        Long allowed = stringRedisTemplate.execute(
                TOKEN_BUCKET_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(nowMillis),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                "1"
        );
        return allowed != null && allowed == 1L;
    }

    private void cachePendingOrder(VoucherOrder voucherOrder) {
        Long orderId = voucherOrder.getId();
        if (orderId == null) {
            return;
        }

        stringRedisTemplate.opsForValue().set(
                pendingOrderKey(orderId),
                JSONUtil.toJsonStr(voucherOrder),
                PENDING_ORDER_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        stringRedisTemplate.opsForValue().set(
                pendingRetryKey(orderId),
                "0",
                PENDING_ORDER_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        stringRedisTemplate.opsForZSet().add(
                PENDING_ORDER_SCHEDULE_KEY,
                orderId.toString(),
                System.currentTimeMillis()
        );
    }

    private void clearPendingOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        List<String> keys = new ArrayList<>(3);
        keys.add(pendingOrderKey(orderId));
        keys.add(pendingRetryKey(orderId));
        keys.add(pendingReturnedKey(orderId));
        stringRedisTemplate.delete(keys);
        stringRedisTemplate.opsForZSet().remove(PENDING_ORDER_SCHEDULE_KEY, orderId.toString());
    }

    private VoucherOrder getPendingOrder(Long orderId) {
        String json = stringRedisTemplate.opsForValue().get(pendingOrderKey(orderId));
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSONUtil.toBean(json, VoucherOrder.class);
        } catch (Exception e) {
            log.error("Failed to parse pending order payload, orderId={}", orderId, e);
            return null;
        }
    }

    private void publishOrderWithPendingRetry(VoucherOrder voucherOrder, String source) {
        if (voucherOrder == null || voucherOrder.getId() == null) {
            return;
        }

        Long orderId = voucherOrder.getId();
        long attempt = incrementPendingRetry(orderId);
        if (attempt > MAX_PENDING_RETRY) {
            handlePendingRetryExhausted(voucherOrder, "pending retry exhausted");
            return;
        }

        clearPendingReturnedFlag(orderId);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConstants.SECKILL_ORDER_EXCHANGE,
                    RabbitMqConstants.SECKILL_ORDER_ROUTING_KEY,
                    voucherOrder,
                    message -> {
                        message.getMessageProperties().setHeader("x-order-id", orderId);
                        return message;
                    },
                    new CorrelationData(orderId.toString())
            );
            schedulePendingRetry(orderId, calcRetryDelayMs(attempt));
            log.debug("Published seckill order, orderId={}, attempt={}, source={}", orderId, attempt, source);
        } catch (Exception e) {
            log.error("Publish failed, orderId={}, attempt={}, source={}", orderId, attempt, source, e);
            if (attempt >= MAX_PENDING_RETRY) {
                handlePendingRetryExhausted(voucherOrder, "publish exception: " + e.getClass().getSimpleName());
                return;
            }
            schedulePendingRetry(orderId, calcRetryDelayMs(attempt));
        }
    }

    private void onPublisherConfirm(CorrelationData correlationData, boolean ack, String cause) {
        Long orderId = correlationData == null ? null : parseOrderId(correlationData.getId());
        if (orderId == null) {
            log.warn("Received publisher confirm without valid correlation id, ack={}, cause={}", ack, cause);
            return;
        }

        if (ack) {
            if (hasPendingReturnedFlag(orderId)) {
                clearPendingReturnedFlag(orderId);
                schedulePendingRetry(orderId, calcRetryDelayMs(currentPendingRetry(orderId)));
                log.warn("Publisher acked but message was returned, reschedule retry, orderId={}", orderId);
                return;
            }
            clearPendingOrder(orderId);
            return;
        }

        log.error("Publisher confirm NACK, orderId={}, cause={}", orderId, cause);
        schedulePendingRetry(orderId, calcRetryDelayMs(currentPendingRetry(orderId)));
    }

    private void onPublisherReturned(Message message, int replyCode, String replyText, String exchange, String routingKey) {
        Long orderId = extractOrderId(message);
        if (orderId == null) {
            log.error("Publisher return without orderId, exchange={}, routingKey={}, replyCode={}",
                    exchange, routingKey, replyCode);
            return;
        }

        markPendingReturnedFlag(orderId);
        log.error("Message returned by broker, orderId={}, exchange={}, routingKey={}, replyCode={}, replyText={}",
                orderId,
                exchange,
                routingKey,
                replyCode,
                replyText);
    }

    private void schedulePendingRetry(Long orderId, long delayMs) {
        if (orderId == null) {
            return;
        }
        long next = System.currentTimeMillis() + Math.max(delayMs, PENDING_RETRY_BASE_DELAY_MS);
        stringRedisTemplate.opsForZSet().add(PENDING_ORDER_SCHEDULE_KEY, orderId.toString(), next);
    }

    private long incrementPendingRetry(Long orderId) {
        Long retry = stringRedisTemplate.opsForValue().increment(pendingRetryKey(orderId));
        stringRedisTemplate.expire(pendingRetryKey(orderId), PENDING_ORDER_TTL_SECONDS, TimeUnit.SECONDS);
        return retry == null ? 1L : retry;
    }

    private long currentPendingRetry(Long orderId) {
        String val = stringRedisTemplate.opsForValue().get(pendingRetryKey(orderId));
        if (StrUtil.isBlank(val)) {
            return 1L;
        }
        Long parsed = parseOrderId(val);
        return parsed == null ? 1L : parsed;
    }

    private long calcRetryDelayMs(long attempt) {
        long safeAttempt = Math.max(1L, attempt);
        long factorShift = Math.min(16L, safeAttempt - 1L);
        long delay = PENDING_RETRY_BASE_DELAY_MS * (1L << factorShift);
        return Math.min(delay, PENDING_RETRY_MAX_DELAY_MS);
    }

    private void handlePendingRetryExhausted(VoucherOrder voucherOrder, String reason) {
        markDeadOrder(voucherOrder, reason);
        compensateReservation(voucherOrder, reason);
        clearPendingOrder(voucherOrder.getId());
    }

    private void markDeadOrder(VoucherOrder voucherOrder, String reason) {
        if (voucherOrder == null || voucherOrder.getId() == null) {
            return;
        }
        Map<String, Object> deadRecord = new HashMap<>(4);
        deadRecord.put("order", voucherOrder);
        deadRecord.put("reason", reason);
        deadRecord.put("time", System.currentTimeMillis());
        stringRedisTemplate.opsForValue().set(
                deadOrderKey(voucherOrder.getId()),
                JSONUtil.toJsonStr(deadRecord),
                DEAD_ORDER_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        log.error("Order marked dead, orderId={}, reason={}", voucherOrder.getId(), reason);
    }

    private long incrementDlqRetry(Long orderId) {
        Long retry = stringRedisTemplate.opsForValue().increment(dlqRetryKey(orderId));
        stringRedisTemplate.expire(dlqRetryKey(orderId), DEAD_ORDER_TTL_SECONDS, TimeUnit.SECONDS);
        return retry == null ? 1L : retry;
    }

    private void clearDlqRetry(Long orderId) {
        if (orderId == null) {
            return;
        }
        stringRedisTemplate.delete(dlqRetryKey(orderId));
    }

    private void compensateReservation(VoucherOrder voucherOrder, String reason) {
        if (voucherOrder == null || voucherOrder.getUserId() == null || voucherOrder.getVoucherId() == null) {
            return;
        }

        VoucherOrder existedOrder = query()
                .select("id")
                .eq("user_id", voucherOrder.getUserId())
                .eq("voucher_id", voucherOrder.getVoucherId())
                .last("LIMIT 1")
                .one();
        if (existedOrder != null) {
            return;
        }

        Long compensated = stringRedisTemplate.execute(
                COMPENSATE_SCRIPT,
                Collections.emptyList(),
                voucherOrder.getVoucherId().toString(),
                voucherOrder.getUserId().toString()
        );
        log.warn("Compensated redis reservation, orderId={}, userId={}, voucherId={}, reason={}, compensated={}",
                voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId(), reason, compensated);
    }

    private void markPendingReturnedFlag(Long orderId) {
        stringRedisTemplate.opsForValue().set(
                pendingReturnedKey(orderId),
                "1",
                PENDING_ORDER_TTL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private boolean hasPendingReturnedFlag(Long orderId) {
        Boolean exists = stringRedisTemplate.hasKey(pendingReturnedKey(orderId));
        return exists != null && exists;
    }

    private void clearPendingReturnedFlag(Long orderId) {
        stringRedisTemplate.delete(pendingReturnedKey(orderId));
    }

    private Long extractOrderId(Message message) {
        if (message == null || message.getMessageProperties() == null) {
            return null;
        }
        Object raw = message.getMessageProperties().getHeaders().get("x-order-id");
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        return raw == null ? null : parseOrderId(raw.toString());
    }

    private Long parseOrderId(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String pendingOrderKey(Long orderId) {
        return PENDING_ORDER_KEY_PREFIX + orderId;
    }

    private String pendingRetryKey(Long orderId) {
        return PENDING_ORDER_RETRY_KEY_PREFIX + orderId;
    }

    private String pendingReturnedKey(Long orderId) {
        return PENDING_ORDER_RETURNED_KEY_PREFIX + orderId;
    }

    private String deadOrderKey(Long orderId) {
        return DEAD_ORDER_KEY_PREFIX + orderId;
    }

    private String dlqRetryKey(Long orderId) {
        return DLQ_RETRY_KEY_PREFIX + orderId;
    }

    @Override
    public Result createVoucherOrder(Long voucherId) {
        return secKillVoucherSync(voucherId);
    }
}
