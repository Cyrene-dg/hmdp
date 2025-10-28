package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;


    @Override
    public Result getShopById(Long id) throws InterruptedException {
       //缓存穿透
//     queryWithPassThrow(id);

        //缓存击穿
//        Shop shop = queryWithMutex(id);
        //逻辑过期
//        Shop shop = queryWithLogicExpire(id);
        //封装好的缓存穿透
//        Shop shop = cacheClient.queryWithPassThrow(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //封装好的逻辑过期
        Shop shop = cacheClient.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY,RedisConstants.LOCK_SHOP_KEY,id,Shop.class,this::getById,RedisConstants.LOGIN_CODE_TTL,TimeUnit.SECONDS);


        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    public Result updateShop(Shop shop) {
        //先改数据库，再删除缓存
        Long id = shop.getId();
        if (id == null) {
           return Result.fail("店铺id不存在");
        }
        updateById(shop);
        //删除缓存
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    /**
     * 解决缓存穿透
     */
    public Shop queryWithPassThrow(Long id) {
        //首先根据id查询redis
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //存在,且不是空值，直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            //转成java对象
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //存在，且是空值(不为空不为null就是空值了)
        if(shopJson != null){
            return null;
        }
        //不存在，查询数据库
        Shop shop = getById(id);
        //数据库为空，保存到redis里空值

        //解决缓存穿透，将空值存入redis里中，再次访问将不会到达数据库，其他的解决方案还有布隆过滤器，一种通过哈希函数先计算索引是否可能存在的过滤器，缓存穿透还需要主动解决，比如规范索引的格式，排除不符合格式的索引等等
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在，将数据库内容存入redis
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key,jsonStr,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //存在，将数据库内容返回给前端
        return shop;

    }

    /**
     * 解决缓存击穿
     * 缓存击穿的问题来源就是热点key失效，大量查询击穿到数据库。
     * 解决方案1，加入互斥锁，一个线程获得锁，查询数据库，其他线程等待那个线程查询完成，之后大家都查内存
     */
    public Shop queryWithMutex(Long id) {
        //首先根据id查询redis
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //存在,且不是空值，直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            //转成java对象
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //存在，且是空值(不为空不为null就是空值了)
        if(shopJson != null){
            return null;
        }
        //不存在，当前线程获取锁，查询数据库
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        try {
            boolean lock = getLock(lockKey);
            //没有获取到
            if (!lock) {
                //等一会
                Thread.sleep(50);
                //再尝试查询
                return queryWithMutex(id);
            }
            //获取到了，查询数据库
            Shop shop = getById(id);
            Thread.sleep(200);
            //数据库为空，保存到redis里空值
            //解决缓存穿透，将空值存入redis里中，再次访问将不会到达数据库，其他的解决方案还有布隆过滤器，一种通过哈希函数先计算索引是否可能存在的过滤器，缓存穿透还需要主动解决，比如规范索引的格式，排除不符合格式的索引等等
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //存在，将数据库内容存入redis
            String jsonStr = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key, jsonStr, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

            //存在，将数据库内容返回给前端
            return shop;
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            //释放锁
            realiseLock(lockKey);
        }
         return null;
    }

    /**
     * 新建线程池
     */
    private  static final ExecutorService CATCH_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);



    /**
     * 关于缓存穿透,的逻辑过期的解决方案，思路就是在没有找到key的时候新开一个线程去找，然后此线程和剩下的所有线程都先返回旧的，旧信息是否过期用逻辑expire来锚定
     * 1.要在保存店铺信息的时候保存逻辑过期时间的属性，使用自定义redisData类，里面两个属性，shop和expiretime
     * 提取把店铺保存到redis里的方法，将逻辑过期也存进去，然后用单元测试测试
     * 2.开始代码逻辑，由于这个属于热点key的存入，先删除缓存穿透的逻辑，然后因为key不会真的过期，所以查不到的情况返回空，查到了再判断逻辑是否过期
     * 接下来就是顺下来的逻辑，查出来的json反序列化，判断逻辑过期，没过期直接返回，过期了开始重建，返回旧信息
     * 重建开始，开启新线程，获取互斥锁，判断锁是否获取成功，成功就开始重建，重建完成后释放锁。
     * 开启线程用线程池，注意在开始重建的时候再检查一遍是否过期
     * @return
     *
     */


    public Shop queryWithLogicExpire(Long id) throws InterruptedException {
        //首先根据id查询redis
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //不存在，从数据库存到缓存，这里不主要解决缓存穿透，测试用的
        if (StrUtil.isBlank(shopJson)) {
            saveShopToRedisWithExpire(id,20L);
        }
            //存在，将内容反序列化，校验逻辑过期时间是否过期
            RedisData redisShopData = JSONUtil.toBean(shopJson, RedisData.class);
            LocalDateTime expireTime = redisShopData.getExpireTime();
            //获取店铺
            JSONObject shopData = (JSONObject) redisShopData.getData();
            Shop shop = JSONUtil.toBean(shopData, Shop.class);

        //没有过期，返回当前店铺
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        //过期了，新线程进行重建
        //先获取锁，再检查一遍过期了没，然后开启新线程
        boolean lock = getLock(RedisConstants.LOCK_SHOP_KEY + id);

        if (lock) {
            // 关键修改：获取锁后重新查询Redis，用最新数据判断是否需要重建（解决第一点问题）
            String latestShopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(latestShopJson)) {
                RedisData latestRedisData = null;
                try {
                    latestRedisData = JSONUtil.toBean(latestShopJson, RedisData.class);
                } catch (Exception e) {
                    stringRedisTemplate.delete(key);
                    log.error("重新查询Redis后反序列化失败，key:{}", key, e);
                    realiseLock(RedisConstants.LOCK_SHOP_KEY + id); // 释放锁
                    return shop; // 返回旧数据
                }
                LocalDateTime latestExpireTime = latestRedisData.getExpireTime();
                // 最新数据未过期，无需重建，直接释放锁
                if (latestExpireTime != null && latestExpireTime.isAfter(LocalDateTime.now())) {
                    realiseLock(RedisConstants.LOCK_SHOP_KEY + id);
                    return JSONUtil.toBean((JSONObject) latestRedisData.getData(), Shop.class);
                }}

            //真的过期了，开启新线程
            CATCH_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建
                    this.saveShopToRedisWithExpire(id, 20L);
                }catch (Exception e){
                    e.printStackTrace();
                }finally {
                    //释放锁
                    this.realiseLock(RedisConstants.LOCK_SHOP_KEY + id);
                }
            });}

        //返回旧数据
        return shop;

    }


    /**
     * 店铺信息和逻辑过期时间存入redis
     * @param id
     * @param expireSeconds
     */
    public void saveShopToRedisWithExpire(Long id, Long expireSeconds) throws InterruptedException {
        Thread.sleep(200);
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        String ShopJsonStr = JSONUtil.toJsonStr(redisData);
        //真实过期时间默认永久有效，只看逻辑过期时间
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,ShopJsonStr);
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
