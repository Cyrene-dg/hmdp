package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;

public interface IShopService extends IService<Shop> {
    Result getShopById(Long id) throws InterruptedException;

    Result createShop(Shop shop);

    Result updateShop(Shop shop);

    Result queryShopById(Integer typeId, Integer current, Double x, Double y);
}