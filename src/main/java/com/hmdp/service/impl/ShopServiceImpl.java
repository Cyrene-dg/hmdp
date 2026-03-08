package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.service.ShopBloomFilterService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopBloomFilterService shopBloomFilterService;

    @Override
    public Result getShopById(Long id) {
        Shop shop = queryShopWithBloomFilter(id);
        if (shop == null) {
            return Result.fail("shop not found");
        }
        return Result.ok(shop);
    }

    //创建更新店铺直接存进布隆
    @Override
    public Result createShop(Shop shop) {
        if (shop == null) {
            return Result.fail("invalid shop payload");
        }
        boolean saved = save(shop);
        if (!saved || shop.getId() == null) {
            return Result.fail("failed to create shop");
        }
        shopBloomFilterService.addShopId(shop.getId());
        return Result.ok(shop.getId());
    }

    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("shop id is required");
        }
        boolean updated = updateById(shop);
        if (!updated) {
            return Result.fail("shop not found");
        }
        shopBloomFilterService.addShopId(id);
        invalidateShopCache(id);
        return Result.ok();
    }

    @Override
    public Result queryShopById(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );

        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            distanceMap.put(shopIdStr, result.getDistance());
        });

        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }

    //使用布隆查询
    private Shop queryShopWithBloomFilter(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String cacheValue = stringRedisTemplate.opsForValue().get(key);

        //redis里有店铺，就直接拿到json，无需查找数据库
        if (StrUtil.isNotBlank(cacheValue)) {
            return JSONUtil.toBean(cacheValue, Shop.class);
        }
        //空值的话就出发了空值缓存逻辑
        if (cacheValue != null) {
            return null;
        }

        //查布隆，一定没有的情况下缓存空置
        if (!shopBloomFilterService.mightContain(id)) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        //真正开始查询数据库，查店铺id，重建缓存，返回查到的店铺，还没查到就还存空值
        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        stringRedisTemplate.opsForValue().set(
                key,
                JSONUtil.toJsonStr(shop),
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.MINUTES
        );
        return shop;
    }

    private void invalidateShopCache(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stringRedisTemplate.delete(key);
        });
    }

    public void saveShopToRedisWithExpire(Long id, Long expireSeconds) throws InterruptedException {
        Thread.sleep(200);
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}
