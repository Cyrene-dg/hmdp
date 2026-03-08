package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.ShopBloomFilterService;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopServiceBloomChainTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ShopBloomFilterService shopBloomFilterService;

    private ShopServiceImpl shopService;

    @BeforeEach
    void setUp() {
        shopService = spy(new ShopServiceImpl());
        ReflectionTestUtils.setField(shopService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(shopService, "shopBloomFilterService", shopBloomFilterService);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getShopById_shouldReturnCachedShop_whenCacheHit() throws Exception {
        Long shopId = 1L;
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + shopId;
        when(valueOperations.get(cacheKey)).thenReturn("{\"id\":1,\"name\":\"coffee\"}");

        Result result = shopService.getShopById(shopId);

        assertTrue(result.getSuccess());
        assertNotNull(result.getData());
        Shop shop = (Shop) result.getData();
        assertEquals(shopId, shop.getId());
        verify(shopBloomFilterService, never()).mightContain(anyLong());
        verify(shopService, never()).getById(anyLong());
    }

    @Test
    void getShopById_shouldShortCircuitAndCacheNull_whenBloomRejects() throws Exception {
        Long shopId = 99L;
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + shopId;
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(shopBloomFilterService.mightContain(shopId)).thenReturn(false);

        Result result = shopService.getShopById(shopId);

        assertFalse(result.getSuccess());
        verify(shopService, never()).getById(shopId);
        verify(valueOperations).set(cacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
    }

    @Test
    void createShop_shouldAddIdToBloom_whenSaveSucceeds() {
        Shop shop = new Shop();
        shop.setId(123L);
        doReturn(true).when(shopService).save(shop);

        Result result = shopService.createShop(shop);

        assertTrue(result.getSuccess());
        assertEquals(123L, result.getData());
        verify(shopBloomFilterService).addShopId(123L);
    }
}
