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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

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
    @Autowired
    private RedissonClient redissonClient;

    private static final Object GLOBAL_LOCK = new Object();
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result secKillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //运行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //判断是否为0
        //不为零，没有购买资格
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //为零，可以购买，将购买信心保存到阻塞队列
        // 构造订单对象
        long orderId = redisIdWoker.nexId("order");
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        // 订单入队（简单入队，不设超时）
        try {
            SECKILL_ORDER_QUEUE.put(order); // 队列满了就阻塞，初学者易理解
        } catch (InterruptedException e) {
            return Result.fail("下单失败");
        }
        //返回订单id
        return Result.ok(orderId);
    }


    // 简单有界阻塞队列：容量5000，直接存订单实体，无额外配置
    private static final BlockingQueue<VoucherOrder> SECKILL_ORDER_QUEUE = new LinkedBlockingQueue<>(5000);
    // 最简单线程池：固定1个线程，默认配置，不用复杂参数
    private static final ExecutorService SECKILL_EXECUTOR = Executors.newFixedThreadPool(1);

    // 应用启动时自动启动线程池消费队列
    @PostConstruct
    public void initConsumer() {
        // 线程池执行消费任务，循环拿队列数据
        //内部匿名类启动队列
        SECKILL_EXECUTOR.execute(() -> {
            while (true) {
                try {
                    // 阻塞获取订单：队列空就等，有数据就取
                    VoucherOrder voucherOrder = SECKILL_ORDER_QUEUE.take();
                    // 处理订单（扣库存+保存）
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    @Transactional
    public void handleVoucherOrder(VoucherOrder voucherOrder) {

        //直接使用voucherOrder来获取订单id和用户id
        Long id = voucherOrder.getId();
        Long voucherId = voucherOrder.getVoucherId();

        //继续进行一人一单的数据库校验
        Integer count = query().eq("user_id", id).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return;
        }

        //核心逻辑，扣减库存
        boolean update = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)//加入乐观锁，在操作数据库的时候再次查看库存是否大于零，由于这一条sql的查询是原子操作，不存在其他线程干扰的问题，即完成了乐观锁.
                .update();//注意，一定去掉类上面的事务注解，必须保证锁必须把所有事务包含进去，不能释放了锁以后事务还没完。

        if (!update) {
            return;
        }

        //核心逻辑，订单保存到数据库
        save(voucherOrder);

    }













    /*
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
//        SimplyRedisLock lock = new SimplyRedisLock("order:"+userId,stringRedisTemplate);

//        boolean tryLock = lock.tryLock(1200L);
//        使用Redission获取锁
        RLock lock = redissonClient.getLock("lock:order" + userId);
        boolean tryLock = lock.tryLock();
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

*/

    //原先的处理订单的逻辑
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


