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

    public void ensureInitialized() {
        RBloomFilter<String> bloomFilter = getFilter();
        if (!bloomFilter.isExists()) {
            bloomFilter.tryInit(
                    RedisConstants.BLOOM_FILTER_EXPECTED_INSERTIONS,
                    RedisConstants.BLOOM_FILTER_FALSE_POSITIVE_PROBABILITY
            );
            log.info("Initialized shop bloom filter: {}", RedisConstants.BLOOM_FILTER_SHOP);
        }
    }

    public boolean mightContain(Long shopId) {
        if (shopId == null) {
            return false;
        }
        RBloomFilter<String> bloomFilter = getFilter();
        if (!bloomFilter.isExists()) {
            // Conservative fallback: allow DB query when bloom filter is unavailable.
            log.warn("Shop bloom filter does not exist, fallback to DB for id={}", shopId);
            return true;
        }
        return bloomFilter.contains(String.valueOf(shopId));
    }

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

    private RBloomFilter<String> getFilter() {
        return redissonClient.getBloomFilter(RedisConstants.BLOOM_FILTER_SHOP);
    }
}
