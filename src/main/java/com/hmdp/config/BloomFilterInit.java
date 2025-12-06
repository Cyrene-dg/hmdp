package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.utils.BloomFilterUtil;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 布隆过滤器初始化类
 * 应用启动时自动加载数据到布隆过滤器，防止缓存穿透
 * 
 * @author hmdp
 */
@Slf4j
@Component
public class BloomFilterInit implements CommandLineRunner {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private VoucherMapper voucherMapper;

    @Resource
    private BloomFilterUtil bloomFilterUtil;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始初始化布隆过滤器...");
        
        // 初始化店铺布隆过滤器
        initShopBloomFilter();
        
        // 初始化用户布隆过滤器
        initUserBloomFilter();
        
        // 初始化优惠券布隆过滤器
        initVoucherBloomFilter();
        
        log.info("布隆过滤器初始化完成！");
    }

    /**
     * 初始化店铺布隆过滤器
     */
    private void initShopBloomFilter() {
        try {
            // 查询所有店铺ID
            List<Shop> shops = shopMapper.selectList(null);
            if (shops == null || shops.isEmpty()) {
                log.warn("店铺数据为空，跳过店铺布隆过滤器初始化");
                return;
            }

            // 创建布隆过滤器
            bloomFilterUtil.createBloomFilter(
                    RedisConstants.BLOOM_FILTER_SHOP,
                    RedisConstants.BLOOM_FILTER_EXPECTED_INSERTIONS,
                    RedisConstants.BLOOM_FILTER_FALSE_POSITIVE_PROBABILITY
            );

            // 将店铺ID添加到布隆过滤器
            List<String> shopIds = shops.stream()
                    .map(shop -> String.valueOf(shop.getId()))
                    .collect(Collectors.toList());
            
            bloomFilterUtil.addBatch(RedisConstants.BLOOM_FILTER_SHOP, shopIds);
            
            log.info("店铺布隆过滤器初始化完成，共加载 {} 个店铺ID", shopIds.size());
        } catch (Exception e) {
            log.error("初始化店铺布隆过滤器失败", e);
        }
    }

    /**
     * 初始化用户布隆过滤器
     */
    private void initUserBloomFilter() {
        try {
            // 查询所有用户ID
            List<User> users = userMapper.selectList(null);
            if (users == null || users.isEmpty()) {
                log.warn("用户数据为空，跳过用户布隆过滤器初始化");
                return;
            }

            // 创建布隆过滤器
            bloomFilterUtil.createBloomFilter(
                    RedisConstants.BLOOM_FILTER_USER,
                    RedisConstants.BLOOM_FILTER_EXPECTED_INSERTIONS,
                    RedisConstants.BLOOM_FILTER_FALSE_POSITIVE_PROBABILITY
            );

            // 将用户ID添加到布隆过滤器
            List<String> userIds = users.stream()
                    .map(user -> String.valueOf(user.getId()))
                    .collect(Collectors.toList());
            
            bloomFilterUtil.addBatch(RedisConstants.BLOOM_FILTER_USER, userIds);
            
            log.info("用户布隆过滤器初始化完成，共加载 {} 个用户ID", userIds.size());
        } catch (Exception e) {
            log.error("初始化用户布隆过滤器失败", e);
        }
    }

    /**
     * 初始化优惠券布隆过滤器
     */
    private void initVoucherBloomFilter() {
        try {
            // 查询所有优惠券ID
            List<Voucher> vouchers = voucherMapper.selectList(null);
            if (vouchers == null || vouchers.isEmpty()) {
                log.warn("优惠券数据为空，跳过优惠券布隆过滤器初始化");
                return;
            }

            // 创建布隆过滤器
            bloomFilterUtil.createBloomFilter(
                    RedisConstants.BLOOM_FILTER_VOUCHER,
                    RedisConstants.BLOOM_FILTER_EXPECTED_INSERTIONS,
                    RedisConstants.BLOOM_FILTER_FALSE_POSITIVE_PROBABILITY
            );

            // 将优惠券ID添加到布隆过滤器
            List<String> voucherIds = vouchers.stream()
                    .map(voucher -> String.valueOf(voucher.getId()))
                    .collect(Collectors.toList());
            
            bloomFilterUtil.addBatch(RedisConstants.BLOOM_FILTER_VOUCHER, voucherIds);
            
            log.info("优惠券布隆过滤器初始化完成，共加载 {} 个优惠券ID", voucherIds.size());
        } catch (Exception e) {
            log.error("初始化优惠券布隆过滤器失败", e);
        }
    }
}

