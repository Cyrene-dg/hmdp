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
    private static final String PENDING_ORDER_KEY_PREFIX = "seckill:pending:order:";
    private static final String DEAD_ORDER_KEY_PREFIX = "seckill:dead:order:";
    private static final long PENDING_ORDER_TTL_SECONDS = 600L;
    private static final long DEAD_ORDER_TTL_SECONDS = 7 * 24 * 3600L;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
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
        long orderId = redisIdWoker.nexId("order");

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );

        if (result == null) {
            return Result.fail("下单失败，请重试");
        }

        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
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
            log.error("秒杀订单投递MQ失败，orderId={}", orderId, e);
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
            log.error("消费秒杀订单失败，投递死信，order={}", voucherOrder, e);
            throw new RuntimeException(e);
        }
    }

    @RabbitListener(queues = RabbitMqConstants.SECKILL_ORDER_DLX_QUEUE)
    public void consumeDeadLetterOrder(VoucherOrder voucherOrder) {
        if (voucherOrder == null || voucherOrder.getId() == null) {
            log.error("收到无效死信消息: {}", voucherOrder);
            return;
        }

        try {
            stringRedisTemplate.opsForValue().set(
                    deadOrderKey(voucherOrder.getId()),
                    JSONUtil.toJsonStr(voucherOrder),
                    DEAD_ORDER_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            log.error("秒杀订单进入死信队列，已记录待人工处理，orderId={}, userId={}, voucherId={}",
                    voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId());
        } catch (Exception e) {
            // Avoid throwing exception here, or DLQ consumer may loop.
            log.error("记录死信订单失败，order={}", voucherOrder, e);
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
                log.error("解析待补偿订单失败，key={}", key, e);
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
                log.error("重试投递秒杀订单失败，orderId={}", voucherOrder.getId(), e);
            }
        }
    }

    private void processVoucherOrder(VoucherOrder voucherOrder) {
        if (voucherOrder == null || voucherOrder.getId() == null || voucherOrder.getUserId() == null || voucherOrder.getVoucherId() == null) {
            // Invalid payload is treated as business drop; no retry needed.
            log.warn("秒杀订单消息不完整，忽略处理：{}", voucherOrder);
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
            log.warn("扣减库存失败，voucherId={}, orderId={}", voucherId, voucherOrder.getId());
            return;
        }

        boolean saved = save(voucherOrder);
        if (!saved) {
            throw new IllegalStateException("保存订单失败, orderId=" + voucherOrder.getId());
        }
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
        return Result.fail("该方法已废弃，请调用秒杀接口");
    }
}
