package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWoker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private RedisIdWoker redisIdWoker;

    // 测试类中创建es，适配IO密集型任务
    private ExecutorService es = new ThreadPoolExecutor(
            20, // 核心线程数（建议CPU核心数×2，如8核设16）
            50, // 最大线程数
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000), // 足够大的队列，避免任务被拒绝
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时，主线程帮忙执行，避免拒绝
    );

    @Test
    public void testSaveShop() throws InterruptedException {
        shopService.saveShopToRedisWithExpire(1L,10L);
    }

    @Test
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


}
