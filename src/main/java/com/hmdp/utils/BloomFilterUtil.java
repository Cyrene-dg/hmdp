package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 布隆过滤器工具类
 * 用于防止缓存穿透，在查询数据库前先判断ID是否存在
 * 
 * @author hmdp
 */
@Slf4j
@Component
public class BloomFilterUtil {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 创建或获取布隆过滤器
     * 
     * @param filterName 过滤器名称
     * @param expectedInsertions 预期插入数量
     * @param falsePositiveProbability 误判率（0-1之间，越小越精确但占用空间越大）
     * @return 布隆过滤器实例
     */
    public RBloomFilter<String> createBloomFilter(String filterName, long expectedInsertions, double falsePositiveProbability) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(filterName);
        
        // 如果布隆过滤器不存在，则初始化
        if (!bloomFilter.isExists()) {
            // 初始化布隆过滤器：预期插入数量、误判率
            bloomFilter.tryInit(expectedInsertions, falsePositiveProbability);
            log.info("布隆过滤器 {} 初始化成功，预期插入数量：{}，误判率：{}", 
                    filterName, expectedInsertions, falsePositiveProbability);
        } else {
            log.info("布隆过滤器 {} 已存在，直接使用", filterName);
        }
        
        return bloomFilter;
    }

    /**
     * 向布隆过滤器中添加元素
     * 
     * @param filterName 过滤器名称
     * @param value 要添加的值
     */
    public void add(String filterName, String value) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(filterName);
        if (bloomFilter.isExists()) {
            bloomFilter.add(value);
        }
    }

    /**
     * 批量向布隆过滤器中添加元素
     * 
     * @param filterName 过滤器名称
     * @param values 要添加的值列表
     */
    public void addBatch(String filterName, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(filterName);
        if (bloomFilter.isExists()) {
            for (String value : values) {
                bloomFilter.add(value);
            }
            log.info("向布隆过滤器 {} 批量添加 {} 个元素", filterName, values.size());
        }
    }

    /**
     * 判断元素是否可能存在
     * 
     * @param filterName 过滤器名称
     * @param value 要判断的值
     * @return true表示可能存在，false表示一定不存在
     */
    public boolean mightContain(String filterName, String value) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(filterName);
        if (!bloomFilter.isExists()) {
            // 如果布隆过滤器不存在，返回true，避免误判（保守策略）
            log.warn("布隆过滤器 {} 不存在，返回true避免误判", filterName);
            return true;
        }
        return bloomFilter.contains(value);
    }

    /**
     * 获取布隆过滤器中已添加的元素数量（近似值）
     * 
     * @param filterName 过滤器名称
     * @return 元素数量
     */
    public long count(String filterName) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(filterName);
        if (bloomFilter.isExists()) {
            return bloomFilter.count();
        }
        return 0;
    }
}

