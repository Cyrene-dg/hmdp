package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private final StringRedisTemplate stringRedisTemplate;

    public ShopTypeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 查询商户列表，核心思路是把整个列表作为值存进redis，而不是每一个元素都用键值对
     */
    @Override
    public Result getTypeList() {

            String key = RedisConstants.CACHE_SHOP_TYPES_KEY;

            String shopJson = stringRedisTemplate.opsForValue().get(key);
            //存在,直接返回
            if (StrUtil.isNotBlank(shopJson)) {
                //转成java对象的列表
                List<ShopType> typeList = JSONUtil.toList(shopJson, ShopType.class);

                return Result.ok(typeList);
            }
            //不存在，查询数据库
            List<ShopType> typeList = query().orderByAsc("sort").list();

            //将数据库内容存入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));
            //将数据库内容返回给前端
            return Result.ok(typeList);

        }
}
