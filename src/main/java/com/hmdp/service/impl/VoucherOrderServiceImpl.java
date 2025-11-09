package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWoker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Autowired
    private RedisIdWoker redisIdWoker;
    @Autowired
    private RedissonClient redissonClient;
    // 注入自身的代理对象
    @Autowired
    private VoucherOrderServiceImpl thisProxy;

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
        long orderId = redisIdWoker.nexId("order");
        //运行lua脚本，订单信息写入消费队列的代码也在lua脚本里
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(orderId)
        );
        //判断是否为0
        //不为零，没有购买资格
        int r = result.intValue();
        if (r != 0 ) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //返回订单id
        return Result.ok(orderId);
    }

    private static final String STREAM_KEY = "stream.orders"; // Stream 键
    private static final String CONSUMER_GROUP = "seckill-order-group"; // 消费者组
    private static final String CONSUMER_NAME = "seckill-consumer-1"; // 消费者


    private final ExecutorService seckillOrderExecutor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "Seckill-Stream-Consumer-Pool") // 自定义线程名称，便于调试
    );


    @PostConstruct
    public void startStreamConsumer() {
        //首先创建消费者组，也能在redis命令中创建。注意消息队列是lua脚本的命令自动创建的
        try {
            stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, CONSUMER_GROUP);
            log.info("消费者组 {} 创建成功", CONSUMER_GROUP);
        } catch (Exception e) {
            log.info("消费者组 {} 已存在", CONSUMER_GROUP);
        }
        //开始循环，从消息队列中读取信息

        // 2. 向线程池提交消费任务
        seckillOrderExecutor.submit(() -> {
            while (true) {
                try {
                    // 3. 读取 Stream 消息（XREADGROUP）
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                            Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                            StreamReadOptions.empty().block(Duration.ofSeconds(1)), // 阻塞1秒
                            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
                    );
//                    对比代码
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
//                    );

                    // 4. 处理消息（无消息则继续循环）
                    if (records == null || records.isEmpty()) {
                        continue;
                    }

                    // 5. 遍历处理每条消息
                    for (MapRecord<String, Object, Object> record : records) {
                        log.info("接收到秒杀订单消息：{}", record);
                        Map<Object, Object> value = record.getValue();

                        // 6. 解析消息字段
                        VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                        //仍然注意获取代理对象防止事务失效
                        thisProxy.handleVoucherOrder(voucherOrder);

//                        对比代码
//                        MapRecord<String, Object, Object> record = list.get(0);
//                        Map<Object, Object> values = record.getValue();
//                        VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);

                        // 8. 确认消息已处理（XACK）
                        stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, record.getId());
                        log.info("订单 {} 处理完成并确认", voucherOrder.getVoucherId());
                    }
                } catch (Exception e) {
                    log.error("处理Stream消息异常", e);
                    //不需要再循环处理消息处理失败的异常了，因为ReadOffset.lastConsumed()能够自动读取
                    try {
                        Thread.sleep(1000); // 异常时休眠1秒，避免循环过快
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }

        //解析信息并处理
        //最后确认信息处理完毕
    }});
    }
    //TODO 逻辑仍然可以优化，首先是出现异常不能无限循环试错，应加入死信队列机制防止无限死循环。其次存入消息队列的消息会永久保存到消息队列中，应该加入定时清理逻辑


//    @Override
//    public Result secKillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        //运行lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString()
//        );
//        //判断是否为0
//        //不为零，没有购买资格
//        int r = result.intValue();
//        if (r != 0) {
//            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
//        }
//        //为零，可以购买，将购买信心保存到阻塞队列
//        // 构造订单对象
//        long orderId = redisIdWoker.nexId("order");
//        VoucherOrder order = new VoucherOrder();
//        order.setId(orderId);
//        order.setUserId(userId);
//        order.setVoucherId(voucherId);
//        // 订单入队（简单入队，不设超时）
//        try {
//            SECKILL_ORDER_QUEUE.put(order); // 队列满了就阻塞，初学者易理解
//        } catch (InterruptedException e) {
//            return Result.fail("下单失败");
//        }
//        //返回订单id
//        return Result.ok(orderId);
//    }
//
//
//    // 简单有界阻塞队列：容量5000，直接存订单实体，无额外配置
//    private static final BlockingQueue<VoucherOrder> SECKILL_ORDER_QUEUE = new LinkedBlockingQueue<>(5000);
//    // 最简单线程池：固定1个线程，默认配置，不用复杂参数
//    private static final ExecutorService SECKILL_EXECUTOR = Executors.newFixedThreadPool(1);
//
//    // 应用启动时自动启动线程池消费队列
//    @PostConstruct
//    public void initConsumer() {
//        // 线程池执行消费任务，循环拿队列数据
//        //内部匿名类启动队列
//        SECKILL_EXECUTOR.execute(() -> {
//            while (true) {
//                try {
//                    // 阻塞获取订单：队列空就等，有数据就取
//                    VoucherOrder voucherOrder = SECKILL_ORDER_QUEUE.take();
//                    // 处理订单（扣库存+保存）
//                    handleVoucherOrder(voucherOrder);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//    }


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


