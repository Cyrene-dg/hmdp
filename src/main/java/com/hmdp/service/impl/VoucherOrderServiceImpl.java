package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
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
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT;

    private static final String PENDING_ORDER_KEY_PREFIX = "seckill:pending:order:";
    private static final String DEAD_ORDER_KEY_PREFIX = "seckill:dead:order:";

    private static final long PENDING_ORDER_TTL_SECONDS = 600L;
    private static final long DEAD_ORDER_TTL_SECONDS = 7 * 24 * 3600L;

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
    }

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    private RedisIdWoker redisIdWoker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public Result secKillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
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

        // Producer-side compensation: if MQ send fails, retry from Redis pending set.
        cachePendingOrder(voucherOrder);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConstants.SECKILL_ORDER_EXCHANGE,
                    RabbitMqConstants.SECKILL_ORDER_ROUTING_KEY,
                    voucherOrder
            );
            clearPendingOrder(orderId);
        } catch (Exception e) {
            log.error("Failed to publish seckill order to MQ, orderId={}", orderId, e);
        }

        return Result.ok(orderId);
    }

    @RabbitListener(queues = RabbitMqConstants.SECKILL_ORDER_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void consumeVoucherOrder(VoucherOrder voucherOrder) {
        try {
            processVoucherOrder(voucherOrder);
        } catch (Exception e) {
            // With default-requeue-rejected=false, throwing exception will route message to DLQ.
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

        try {
            stringRedisTemplate.opsForValue().set(
                    deadOrderKey(voucherOrder.getId()),
                    JSONUtil.toJsonStr(voucherOrder),
                    DEAD_ORDER_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            log.error("Order entered DLQ, waiting for manual handling, orderId={}, userId={}, voucherId={}",
                    voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId());
        } catch (Exception e) {
            // Avoid throwing exception here, or DLQ consumer may loop.
            log.error("Failed to record dead-letter order, order={}", voucherOrder, e);
        }
    }

    @Scheduled(fixedDelay = 5000L)
    public void retryPendingOrders() {
        Set<String> keys = stringRedisTemplate.keys(PENDING_ORDER_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isBlank(json)) {
                stringRedisTemplate.delete(key);
                continue;
            }

            VoucherOrder voucherOrder;
            try {
                voucherOrder = JSONUtil.toBean(json, VoucherOrder.class);
            } catch (Exception e) {
                log.error("Failed to parse pending order, key={}", key, e);
                stringRedisTemplate.delete(key);
                continue;
            }

            try {
                rabbitTemplate.convertAndSend(
                        RabbitMqConstants.SECKILL_ORDER_EXCHANGE,
                        RabbitMqConstants.SECKILL_ORDER_ROUTING_KEY,
                        voucherOrder
                );
                clearPendingOrder(voucherOrder.getId());
            } catch (Exception e) {
                log.error("Retry publish failed for pending order, orderId={}", voucherOrder.getId(), e);
            }
        }
    }

    private void processVoucherOrder(VoucherOrder voucherOrder) {
        if (voucherOrder == null || voucherOrder.getId() == null || voucherOrder.getUserId() == null || voucherOrder.getVoucherId() == null) {
            // Invalid payload is treated as business drop; no retry needed.
            log.warn("Incomplete seckill order payload, skip: {}", voucherOrder);
            return;
        }

        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // Business duplicate: ack directly, no retry.
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return;
        }

        // DB-side optimistic stock check (second safety net).
        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!update) {
            // Business sold out: ack directly, no retry.
            log.warn("Stock update failed, voucherId={}, orderId={}", voucherId, voucherOrder.getId());
            return;
        }

        boolean saved = save(voucherOrder);
        if (!saved) {
            throw new IllegalStateException("Failed to save order, orderId=" + voucherOrder.getId());
        }
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
        stringRedisTemplate.opsForValue().set(
                pendingOrderKey(voucherOrder.getId()),
                JSONUtil.toJsonStr(voucherOrder),
                PENDING_ORDER_TTL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void clearPendingOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        stringRedisTemplate.delete(pendingOrderKey(orderId));
    }

    private String pendingOrderKey(Long orderId) {
        return PENDING_ORDER_KEY_PREFIX + orderId;
    }

    private String deadOrderKey(Long orderId) {
        return DEAD_ORDER_KEY_PREFIX + orderId;
    }

    @Override
    public Result createVoucherOrder(Long voucherId) {
        return Result.fail("This method is deprecated, please call seckill endpoint");
    }
}
