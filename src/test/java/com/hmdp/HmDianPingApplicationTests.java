package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWoker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private RedisIdWoker redisIdWoker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 测试类中创建es，适配IO密集型任务
    private ExecutorService es = new ThreadPoolExecutor(
            20, // 核心线程数（建议CPU核心数×2，如8核设16）
            50, // 最大线程数
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000), // 足够大的队列，避免任务被拒绝
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时，主线程帮忙执行，避免拒绝
    );

//    @Test
    public void testSaveShop() throws InterruptedException {
        shopService.saveShopToRedisWithExpire(1L,10L);
    }

//    @Test
    public void testIdWork() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            try { // 新增try-catch
                for (int i = 0; i < 100; i++) {
                    long id = redisIdWoker.nexId("order");
                     System.out.println("id = "+id); // 可选：注释打印，减少IO耗时
                }
            } catch (Exception e) {
                e.printStackTrace(); // 打印异常，排查Redis问题
            } finally { // 无论是否异常，都必须countDown
                latch.countDown();
            }
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = "+(end - begin));
    }

    //往redis里添加现有的店铺的位置信息
//    @Test
    @DirtiesContext
    void loadShopData(){
        //查询所有店铺的信息封装到一个集合
        List<Shop> list = shopService.list();
        //店铺按照typeId分组，店铺类型相同的放到一个集合里
        Map<Long,List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //循环写入redis里
        //Entry相当于一个map的包装器，遍历的不能直接是map，先转换成set载遍历
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
//                stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

//    @Test
//    void testHyperLogLog() {
//        // 准备数组，装用户数据
//        String[] users = new String[1000];
//        // 数组角标
//        int index = 0;
//        for (int i = 1; i <= 1000000; i++) {
//            // 赋值
//            users[index++] = "user_" + i;
//            // 每1000条发送一次
//            if (i % 1000 == 0) {
//                index = 0;
//                stringRedisTemplate.opsForHyperLogLog().add("hll1", users);
//            }
//        }
//        // 统计数量
//        Long size = stringRedisTemplate.opsForHyperLogLog().size("hll1");
//        System.out.println("size = " + size);
//    }

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if(j == 999){
                // 发送到Redis
                stringRedisTemplate.opsForHyperLogLog().add("hll2", values);
            }
        }
        // 统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hll2");
        System.out.println("count = " + count);
    }

}
