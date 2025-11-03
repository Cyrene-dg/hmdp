package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimplyRedisLock implements  ILock{

    private static final String KEY_PREFIX = "lock:";
    //由于锁没有唯一标识，导致别人可以释放自己的锁。所以利用uuid给锁加入标识，释放锁的时候如果不是自己的锁不能释放

    //别加staticfinal，要不然所有uuid都是一样的
    private  String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    //业务的名称，需要传过来区分不同业务的锁
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimplyRedisLock(String name,StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean tryLock(Long timeoutSec) {
        //获取锁的同时设置超时时间（一条命令相当于原子化设置）
        //锁的命令用idabsent这个命令加上过期时间，   key使用前缀拼上名字，value用当前线程对象获取的用户id
        //这里value改为UUID标识加上线程id，注意是线程id不是用户id。当然，也可以只用uuid就行，毕竟这里能够保证全局唯一就行
        //总结，锁的键起到标识作用，如何拼接呢，先表明这是一个锁lock:然后表明这是哪个接口用的锁，传入order:,最后，表明是哪个用户的锁，获取用户id
        //这里容易混淆，以上的锁解决的是一个用户多买的问题，是用户锁。而防止库存超卖这种问题的属于商品锁，这样的锁在之前已经学习过，就是在操作数据库的时候保持原子性
        String value = ID_PREFIX+Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, value, timeoutSec, TimeUnit.MINUTES);
        //防止自动拆箱产生的空指针
        return /*BooleanUtil.isTrue(success);*/ Boolean.TRUE.equals(success);
        //获取失败，直接返回false，相当于同一个用户买两次优惠券，这是禁止的
        //获取成功，这个时候执行操作
        //fianlly释放锁

    }

    @Override
    public void unlock() {

        //获取当前线程的value
        String value = ID_PREFIX+Thread.currentThread().getId();
        //获取redis里存的锁的value
        String redisValue = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //对比二者
        //由于对比的过程仍然不具备原子性，那就使用lua脚本将对比和释放锁的过程绑定成原子操作。这里先不实现
        if(value.equals(redisValue)){
//            相同就释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}

//以上的以setnx命令为基础的锁存在四个问题
//第一个是不可重入，方法a获取锁之后，同一个线程的方法b也要拿到这把锁，但是这把锁不能被重复拿到
//第二个是不可重试，获取锁失败以后直接不管了，但合理的逻辑应该是再重试拿到锁
//第三个是超时释放，锁设置了过期时间，但是业务执行流程大于设置的过期时间，导致业务还没有完成锁就被释放了
//第四个是主从一致性，主节点突然宕机，但是信息没来得及同步到从节点，于是从节点将锁又给了其他用户
//为了解决这四个问题，我们引入Redisson来获得更好用的锁
