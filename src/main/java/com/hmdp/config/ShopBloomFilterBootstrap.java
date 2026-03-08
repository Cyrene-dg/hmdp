package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.ShopBloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class ShopBloomFilterBootstrap implements CommandLineRunner {

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private ShopBloomFilterService shopBloomFilterService;

    @Override
    public void run(String... args) {
        shopBloomFilterService.ensureInitialized();
        List<Shop> shops = shopMapper.selectList(null);
        if (shops == null || shops.isEmpty()) {
            log.warn("No shop data found when preheating bloom filter");
            return;
        }
        for (Shop shop : shops) {
            if (shop != null && shop.getId() != null) {
                shopBloomFilterService.addShopId(shop.getId());
            }
        }
        log.info("Shop bloom filter preheated, loaded {} shop ids", shops.size());
    }
}
