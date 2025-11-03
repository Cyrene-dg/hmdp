package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWoker;
import com.hmdp.utils.SimplyRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Autowired
    private RedisIdWoker redisIdWoker;

    private static final Object GLOBAL_LOCK = new Object();
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

        Long userId = UserHolder.getUser().getId();
        //获取锁对象
        SimplyRedisLock lock = new SimplyRedisLock(RedisConstants.LOCK_ORDER_KEY+userId,stringRedisTemplate);

        boolean tryLock = lock.tryLock(1200L);
        //判断成功失败，失败返回错误信息
        if(!tryLock){
            return Result.fail("不能重复下单");
        }


        //两点注意，一是必须在整个方法外加锁，不然无法把事务包含进去，第二是不能直接用当前方法的实例，因为没有加事务，导致spring事务失效，必须拿到代理对象，即不能事务没提交就释放锁
        try{
        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        return proxy.createVoucherOrder(voucherId);
        }finally {
            //释放锁
            lock.unlock();
        }
    }


    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //还有库存，操作数据库，将数据库内秒杀劵的数量减一
        //先完成一人一单的逻辑，先通过用户id和优惠券id查询唯一的订单是否存在，如果存在，就不能够再买了

        Long id = UserHolder.getUser().getId();

        Integer count = query().eq("user_id", id).eq("voucher_id", voucherId).count();
        if(count>0){
            return Result.fail("该用户已经购买优惠券，不能重复购买");
        }

        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)//加入乐观锁，在操作数据库的时候再次查看库存是否大于零，由于这一条sql的查询是原子操作，不存在其他线程干扰的问题，即完成了乐观锁.
                .update();//注意，一定去掉类上面的事务注解，必须保证锁必须把所有事务包含进去，不能释放了锁以后事务还没完。

        if(!update){
            return Result.fail("库存不足");
        }
        //成功扣减库存，向前端返回订单信息
        //包括订单的全局唯一id，下单的用户id，当前的优惠券id
        VoucherOrder voucherOrder = new VoucherOrder();
        long order = redisIdWoker.nexId("order");

        voucherOrder.setId(order);
        voucherOrder.setUserId(id);
        voucherOrder.setVoucherId(voucherId);

        //保存到订单表里
        save(voucherOrder);


        return Result.ok(voucherOrder);
    }
}
