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

/**
 * <p>
 *  鏈嶅姟瀹炵幇绫?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final String PENDING_ORDER_KEY_PREFIX = "seckill:pending:order:";
    private static final long PENDING_ORDER_TTL_SECONDS = 600L;

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

        // 先落一份待投递记录，MQ发送失败时由定时任务补偿。
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
    @Transactional
    public void consumeVoucherOrder(VoucherOrder voucherOrder) {
        processVoucherOrder(voucherOrder);
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
            log.warn("秒杀订单消息不完整，忽略处理：{}", voucherOrder);
            return;
        }

        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return;
        }

        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!update) {
            log.warn("扣减库存失败，voucherId={}, orderId={}", voucherId, voucherOrder.getId());
            return;
        }

        save(voucherOrder);
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

    @Override
    public Result createVoucherOrder(Long voucherId) {
        return Result.fail("该方法已废弃，请调用秒杀接口");
    }
}
