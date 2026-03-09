package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Autowired
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(
            @PathVariable("id") Long voucherId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId
    ) {
        return voucherOrderService.secKillVoucher(voucherId, requestId);
    }

    @PostMapping("seckill-sync/{id}")
    public Result seckillVoucherSync(
            @PathVariable("id") Long voucherId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId
    ) {
        return voucherOrderService.secKillVoucherSync(voucherId, requestId);
    }
}
