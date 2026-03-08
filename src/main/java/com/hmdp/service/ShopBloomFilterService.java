package com.hmdp.service;

import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class ShopBloomFilterService {

    @Resource
    private RedissonClient redissonClient;

    //确保布隆过滤器已初始化
    public void ensureInitialized() {
        RBloomFilter<String> bloomFilter = getFilter();
        //如果没有初始化尝试初始化
        if (!bloomFilter.isExists()) {
            bloomFilter.tryInit(
                    RedisConstants.BLOOM_FILTER_EXPECTED_INSERTIONS,
                    RedisConstants.BLOOM_FILTER_FALSE_POSITIVE_PROBABILITY
            );
            log.info("Initialized shop bloom filter: {}", RedisConstants.BLOOM_FILTER_SHOP);
        }
    }

    //查询店铺id是否可能存在
    public boolean mightContain(Long shopId) {
        if (shopId == null) {
            return false;
        }
        RBloomFilter<String> bloomFilter = getFilter();
        //再次检查布隆过滤器是否可用
        if (!bloomFilter.isExists()) {
            // Conservative fallback: allow DB query when bloom filter is unavailable.
            log.warn("Shop bloom filter does not exist, fallback to DB for id={}", shopId);
            return true;
        }
        //核心代码，返回是否可能存在
        return bloomFilter.contains(String.valueOf(shopId));
    }

    //将店铺id写入布隆过滤器
    public void addShopId(Long shopId) {
        if (shopId == null) {
            return;
        }
        ensureInitialized();
        getFilter().add(String.valueOf(shopId));
    }

    public void addShopId(String shopId) {
        if (shopId == null || shopId.trim().isEmpty()) {
            return;
        }
        ensureInitialized();
        getFilter().add(shopId);
    }

    //封装获取布隆的逻辑
    private RBloomFilter<String> getFilter() {
        return redissonClient.getBloomFilter(RedisConstants.BLOOM_FILTER_SHOP);
    }
}
