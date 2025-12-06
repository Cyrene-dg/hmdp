package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;


    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String  CACHE_SHOP_TYPES_KEY= "cache:shop:types";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final String LOCK_ORDER_KEY = "lock:order";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String FOLLOW_KEY = "follow:";

    // 布隆过滤器相关常量
    public static final String BLOOM_FILTER_SHOP = "bloom:filter:shop";
    public static final String BLOOM_FILTER_USER = "bloom:filter:user";
    public static final String BLOOM_FILTER_VOUCHER = "bloom:filter:voucher";
    
    // 布隆过滤器配置：预期插入数量（可根据实际数据量调整）
    public static final long BLOOM_FILTER_EXPECTED_INSERTIONS = 10000L;
    // 布隆过滤器误判率（0.01表示1%的误判率，越小越精确但占用空间越大）
    public static final double BLOOM_FILTER_FALSE_POSITIVE_PROBABILITY = 0.01;
}
