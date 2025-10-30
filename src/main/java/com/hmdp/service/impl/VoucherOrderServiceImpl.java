package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWoker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Transactional
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Autowired
    private RedisIdWoker redisIdWoker;

    @Override
    public Result secKillVoucher(Long voucherId) {
        //通过优惠券id查询优惠卷，类型是秒杀劵，引入秒杀卷管理service
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //判断秒杀卷的时间是否合理
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("优惠券发售未开始");
        }
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("优惠券售卖已结束");
        }
        //判断秒杀卷的库存
        if(seckillVoucher.getStock()<1){
            return Result.fail("库存不足");
        }
        //还有库存，操作数据库，将数据库内秒杀劵的数量减一
        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)//加入乐观锁，在操作数据库的时候再次查看库存是否大于零，由于这一条sql的查询是原子操作，不存在其他线程干扰的问题，即完成了乐观锁
                .update();

        if(!update){
           return Result.fail("库存不足");
        }
        //成功扣减库存，向前端返回订单信息
        //包括订单的全局唯一id，下单的用户id，当前的优惠券id
        VoucherOrder voucherOrder = new VoucherOrder();
        long order = redisIdWoker.nexId("order");
        Long id = UserHolder.getUser().getId();

        voucherOrder.setId(order);
        voucherOrder.setUserId(id);
        voucherOrder.setVoucherId(voucherId);


        return Result.ok(voucherOrder);
    }
}
