package com.hmdp.utils;

public interface ILock {
    //ilock接口，尝试获取锁
    boolean tryLock(Long timeoutSec);
    //释放锁
    void unlock();

}
