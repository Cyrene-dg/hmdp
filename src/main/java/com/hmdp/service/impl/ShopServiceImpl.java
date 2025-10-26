package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
    @Override
    public Result getShopById(Long id) {
        //首先根据id查询redis
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //存在,且不是空值，直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            //转成java对象
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //存在，且是空值(不为空不为null就是空值了)
        if(shopJson != null){
            return Result.fail("店铺不能为空");
        }
        //不存在，查询数据库
        Shop shop = getById(id);
        //数据库为空，保存到redis里空值

        //解决缓存穿透，将空值存入redis里中，再次访问将不会到达数据库，其他的解决方案还有布隆过滤器，一种通过哈希函数先计算索引是否可能存在的过滤器，缓存穿透还需要主动解决，比如规范索引的格式，排除不符合格式的索引等等
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        //存在，将数据库内容存入redis
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key,jsonStr,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //存在，将数据库内容返回给前端
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
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY);
        return Result.ok();
    }
}
