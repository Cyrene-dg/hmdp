package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
//为了解决redis实现全局唯一id的问题，这里提供一种方法来实现全局唯一id
//id构成，六十四位二进制，前三十二位使用当前时间到初始时间的差值，后三十二位生成全局唯一的序列号，构成是前缀加上redis自己的自增id加上一个当前时间的时间戳
public class RedisIdWoker {

    //初始时间
    private static final long BEGIN_TIMESTAMP = 1704067200L;

    //序列号位移数量
    private static final long COUNT_BITS = 32L;

    @Autowired
    private  StringRedisTemplate stringRedisTemplate;

    public long nexId(String keyPrefix){
        //生成时间戳
    //获取当前时间再减去初试时间
        LocalDateTime now = LocalDateTime.now();
        long seconds = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = seconds - BEGIN_TIMESTAMP;

        //生成序列号

        //生成一个精确到天的日期，加入到key中，保证每天回滚序列号，防止二的六十四次方的序列号用完
        String data = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + data);
        //拼接并返回

        return timeStamp << COUNT_BITS | count;

    }

//    //用来获取初始时间
//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2024,1,1,0,0,0);
//        long second = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println(second);
//    }
}
