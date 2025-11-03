package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimplyRedisLock implements  ILock{

    private static final String KEY_PREFIX = "lock:";

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
        String value = Thread.currentThread().getId()+"";
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, value, timeoutSec, TimeUnit.MINUTES);
        //防止自动拆箱产生的空指针
        return /*BooleanUtil.isTrue(success);*/ Boolean.TRUE.equals(success);
        //获取失败，直接返回false，相当于同一个用户买两次优惠券，这是禁止的
        //获取成功，这个时候执行操作
        //fianlly释放锁

    }

    @Override
    public void unlock() {

        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
