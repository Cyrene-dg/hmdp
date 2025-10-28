package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 方法一，不带逻辑过期的存数据
     * 参数：键，值，过期时间,时间单位
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    /**
     * 方法二，带逻辑过期时间的存数据
     * 参数，键，值，逻辑过期时间，时间单位
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 对缓存穿透进行封装
     * 参数，key前缀，id，类的泛型（返回值的类型），调用的函数，过期时间和时间类型
     * @param keyPrefix
     * @param id
     * @param clazz
     * @param dbFallBack
     * @param time
     * @param timeUnit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R,ID> R queryWithPassThrow(String keyPrefix, ID id, Class<R> clazz, Function<ID,R> dbFallBack,Long time
    ,TimeUnit timeUnit) {
        //首先根据id查询redis
        String key = keyPrefix + id;

        String json = stringRedisTemplate.opsForValue().get(key);
        //存在,且不是空值，直接返回
        if (StrUtil.isNotBlank(json)) {
            //转成java对象
            R r = JSONUtil.toBean(json, clazz);
            return r;
        }
        //存在，且是空值(不为空不为null就是空值了)
        if(json != null){
            return null;
        }
        //不存在，查询数据库
        R r = dbFallBack.apply(id);
        //数据库为空，保存到redis里空值

        //解决缓存穿透，将空值存入redis里中，再次访问将不会到达数据库，其他的解决方案还有布隆过滤器，一种通过哈希函数先计算索引是否可能存在的过滤器，缓存穿透还需要主动解决，比如规范索引的格式，排除不符合格式的索引等等
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key,"",time, timeUnit);
            return null;
        }
        //存在，将数据库内容存入redis
        String jsonStr = JSONUtil.toJsonStr(r);
        stringRedisTemplate.opsForValue().set(key,jsonStr,time, timeUnit);

        //存在，将数据库内容返回给前端
        return r;

    }


    /**
     * 新建线程池
     */
    private  static final ExecutorService CATCH_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public <R,ID> R queryWithLogicExpire(String keyPrefix,String lockPrefix,ID id,Class<R> clazz,Function<ID,R> dbFallBack,Long logicTime,TimeUnit timeUnit) throws InterruptedException {
        //首先根据id查询redis
        String key = keyPrefix + id;

        String json = stringRedisTemplate.opsForValue().get(key);
        //不存在，直接为null，先存进去再测试吧
        if (StrUtil.isBlank(json)) {
           return null;
        }
        //存在，将内容反序列化，校验逻辑过期时间是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //获取店铺
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, clazz);

        //没有过期，返回当前店铺
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        //过期了，新线程进行重建
        //先获取锁，再检查一遍过期了没，然后开启新线程
        boolean lock = getLock(lockPrefix + id);

        if (lock) {
            // 关键修改：获取锁后重新查询Redis，用最新数据判断是否需要重建（解决第一点问题）
            String latestJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(latestJson)) {
                RedisData latestRedisData = null;
                try {
                    latestRedisData = JSONUtil.toBean(latestJson, RedisData.class);
                } catch (Exception e) {
                    stringRedisTemplate.delete(key);
                    log.error("重新查询Redis后反序列化失败，key:{}", key, e);
                    realiseLock(lockPrefix + id); // 释放锁
                    return r; // 返回旧数据
                }
                LocalDateTime latestExpireTime = latestRedisData.getExpireTime();
                // 最新数据未过期，无需重建，直接释放锁
                if (latestExpireTime != null && latestExpireTime.isAfter(LocalDateTime.now())) {
                    realiseLock(lockPrefix+ id);
                    return JSONUtil.toBean((JSONObject) latestRedisData.getData(), clazz);
                }}

            //真的过期了，开启新线程
            CATCH_REBUILD_EXECUTOR.submit(()->{
                try {
                    R r1 = dbFallBack.apply(id);
                    //重建
                    setWithLogicExpire(key,r1,logicTime,timeUnit);
                }catch (Exception e){
                    e.printStackTrace();
                }finally {
                    //释放锁
                    this.realiseLock(lockPrefix + id);
                }
            });}

        //返回旧数据
        return r;
    }



    /**
     * 获取互斥锁
     * @param key
     * @return
     */
    private boolean getLock(String key) {
        Boolean set = stringRedisTemplate.opsForValue().setIfAbsent(key, "", RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(set);
    }

    /**
     * 释放互斥锁
     * @param key
     * @return
     */
    private  boolean realiseLock(String key) {
        Boolean delete = stringRedisTemplate.delete(key);
        return BooleanUtil.isTrue(delete);
    }


}
