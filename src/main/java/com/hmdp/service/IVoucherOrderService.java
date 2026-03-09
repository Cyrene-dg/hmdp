package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;

/**
 * <p>
 *  秒杀订单服务
 * </p>
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result secKillVoucher(Long voucherId);

    Result secKillVoucher(Long voucherId, String requestId);

    Result secKillVoucherSync(Long voucherId);

    Result secKillVoucherSync(Long voucherId, String requestId);

    Result createVoucherOrder(Long voucherId);
}
