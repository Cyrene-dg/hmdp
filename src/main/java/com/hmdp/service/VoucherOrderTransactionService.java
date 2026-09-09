package com.hmdp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 秒杀订单的数据库事务边界。
 *
 * <p>该类必须独立于 RabbitMQ 监听器 Bean：监听器在事务外识别重复消息，
 * 本类只负责让“扣数据库库存 + 写订单”要么一起提交，要么一起回滚。</p>
 */
@Service
public class VoucherOrderTransactionService {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Transactional(rollbackFor = Exception.class)
    public PersistResult persist(VoucherOrder voucherOrder) {
        validate(voucherOrder);

        if (orderExists(voucherOrder.getUserId(), voucherOrder.getVoucherId())) {
            return PersistResult.ALREADY_EXISTS;
        }

        boolean stockUpdated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!stockUpdated) {
            return PersistResult.STOCK_UNAVAILABLE;
        }

        // 不捕获 DuplicateKeyException：并发重复消息触发唯一索引时，
        // 该异常必须离开事务方法，令前面的数据库扣库存一并回滚。
        voucherOrderMapper.insert(voucherOrder);
        return PersistResult.CREATED;
    }

    public boolean orderExists(Long userId, Long voucherId) {
        if (userId == null || voucherId == null) {
            return false;
        }
        Integer count = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId));
        return count != null && count > 0;
    }

    private void validate(VoucherOrder voucherOrder) {
        if (voucherOrder == null || voucherOrder.getId() == null
                || voucherOrder.getUserId() == null || voucherOrder.getVoucherId() == null) {
            throw new IllegalArgumentException("Incomplete seckill order payload");
        }
    }

    public enum PersistResult {
        CREATED,
        ALREADY_EXISTS,
        STOCK_UNAVAILABLE
    }
}
