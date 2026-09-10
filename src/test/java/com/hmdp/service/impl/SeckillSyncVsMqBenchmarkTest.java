package com.hmdp.service.impl;

import com.qinghe.marketing.QingheMarketingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = QingheMarketingApplication.class, properties = {
        "legacy.hmdp.endpoints-enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@AutoConfigureMockMvc
class SeckillSyncVsMqBenchmarkTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void benchmarkSyncVsMq() throws Exception {
        long voucherId = 901L;
        int requests = 2000;
        int concurrency = 50;

        BenchmarkResult asyncResult = runScenario(
                "mq_async",
                "/voucher-order/seckill/{id}",
                voucherId,
                requests,
                concurrency,
                2_000_000L
        );

        BenchmarkResult syncResult = runScenario(
                "sync_direct",
                "/voucher-order/seckill-sync/{id}",
                voucherId,
                requests,
                concurrency,
                4_000_000L
        );

        Path out = Path.of("target", "seckill_sync_vs_mq_benchmark.csv");
        List<String> lines = new ArrayList<>();
        lines.add("scenario,requests,concurrency,success,fail,fail_rate_pct,avg_ms,p95_ms,p99_ms,qps,wall_sec");
        lines.add(asyncResult.toCsvLine());
        lines.add(syncResult.toCsvLine());
        Files.write(out, lines, StandardCharsets.UTF_8);

        System.out.println(asyncResult.toReadableLine());
        System.out.println(syncResult.toReadableLine());
        System.out.println("CSV=" + out.toAbsolutePath());
    }

    private BenchmarkResult runScenario(
            String scenario,
            String endpoint,
            long voucherId,
            int requests,
            int concurrency,
            long baseUserId
    ) throws Exception {
        prepareVoucherData(voucherId, requests + 2000);
        List<String> tokenKeys = prepareTokens(scenario, baseUserId, requests);
        long[] latencies = new long[requests];

        AtomicInteger seq = new AtomicInteger(0);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);

        long wallStart = System.nanoTime();
        for (int t = 0; t < concurrency; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    while (true) {
                        int i = seq.getAndIncrement();
                        if (i >= requests) {
                            break;
                        }
                        long userId = baseUserId + i;
                        clearRateLimit(voucherId, userId);

                        String token = tokenKeys.get(i).substring("login:token:".length());
                        long begin = System.nanoTime();
                        MvcResult result = mockMvc.perform(
                                        post(endpoint, voucherId)
                                                .header("Authorization", token)
                                )
                                .andReturn();
                        long cost = System.nanoTime() - begin;
                        latencies[i] = cost;

                        int status = result.getResponse().getStatus();
                        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                        if (status == 200 && body.contains("\"success\":true")) {
                            success.incrementAndGet();
                        } else {
                            fail.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();
        long wallEnd = System.nanoTime();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        // cleanup benchmark tokens
        stringRedisTemplate.delete(tokenKeys);

        Arrays.sort(latencies);
        double wallSec = (wallEnd - wallStart) / 1_000_000_000.0;
        double avgMs = Arrays.stream(latencies).average().orElse(0D) / 1_000_000.0;
        double p95Ms = latencies[(int) Math.ceil(requests * 0.95) - 1] / 1_000_000.0;
        double p99Ms = latencies[(int) Math.ceil(requests * 0.99) - 1] / 1_000_000.0;
        double qps = requests / wallSec;
        double failRate = fail.get() * 100.0 / requests;

        return new BenchmarkResult(
                scenario,
                requests,
                concurrency,
                success.get(),
                fail.get(),
                failRate,
                avgMs,
                p95Ms,
                p99Ms,
                qps,
                wallSec
        );
    }

    private void prepareVoucherData(long voucherId, int stock) {
        jdbcTemplate.update("DELETE FROM tb_voucher_order WHERE voucher_id = ?", voucherId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime begin = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        jdbcTemplate.update(
                "INSERT INTO tb_seckill_voucher(voucher_id, stock, create_time, begin_time, end_time, update_time) " +
                        "VALUES (?, ?, NOW(), ?, ?, NOW()) " +
                        "ON DUPLICATE KEY UPDATE stock = VALUES(stock), begin_time = VALUES(begin_time), end_time = VALUES(end_time), update_time = NOW()",
                voucherId, stock, begin, end
        );

        String stockKey = "seckill:stock:" + voucherId;
        String orderKey = "seckill:order:" + voucherId;
        stringRedisTemplate.delete(Arrays.asList(stockKey, orderKey));
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(stock));

        clearRateLimit(voucherId, -1L);

        Set<String> pending = stringRedisTemplate.keys("seckill:pending:order:*");
        if (pending != null && !pending.isEmpty()) {
            stringRedisTemplate.delete(pending);
        }
    }

    private List<String> prepareTokens(String scenario, long baseUserId, int requests) {
        List<String> keys = new ArrayList<>(requests);
        for (int i = 0; i < requests; i++) {
            long userId = baseUserId + i;
            String token = scenario + "-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            String tokenKey = "login:token:" + token;

            Map<String, String> map = new HashMap<>(4);
            map.put("id", String.valueOf(userId));
            map.put("nickName", "bench_" + userId);
            map.put("icon", "");
            stringRedisTemplate.opsForHash().putAll(tokenKey, map);
            stringRedisTemplate.expire(tokenKey, 30, TimeUnit.MINUTES);
            keys.add(tokenKey);
        }
        return keys;
    }

    private void clearRateLimit(long voucherId, long userId) {
        List<String> keys = new ArrayList<>(3);
        keys.add("rate:limit:seckill:global");
        keys.add("rate:limit:seckill:voucher:" + voucherId);
        if (userId > 0) {
            keys.add("rate:limit:seckill:user:" + userId);
        }
        stringRedisTemplate.delete(keys);
    }

    private static class BenchmarkResult {
        final String scenario;
        final int requests;
        final int concurrency;
        final int success;
        final int fail;
        final double failRatePct;
        final double avgMs;
        final double p95Ms;
        final double p99Ms;
        final double qps;
        final double wallSec;

        BenchmarkResult(String scenario, int requests, int concurrency, int success, int fail,
                        double failRatePct, double avgMs, double p95Ms, double p99Ms,
                        double qps, double wallSec) {
            this.scenario = scenario;
            this.requests = requests;
            this.concurrency = concurrency;
            this.success = success;
            this.fail = fail;
            this.failRatePct = failRatePct;
            this.avgMs = avgMs;
            this.p95Ms = p95Ms;
            this.p99Ms = p99Ms;
            this.qps = qps;
            this.wallSec = wallSec;
        }

        String toCsvLine() {
            return String.format(Locale.ROOT,
                    "%s,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.2f,%.4f",
                    scenario, requests, concurrency, success, fail,
                    failRatePct, avgMs, p95Ms, p99Ms, qps, wallSec);
        }

        String toReadableLine() {
            return String.format(Locale.ROOT,
                    "%s => success=%d fail=%d failRate=%.2f%% avg=%.2fms p95=%.2fms p99=%.2fms qps=%.2f wall=%.2fs",
                    scenario, success, fail, failRatePct, avgMs, p95Ms, p99Ms, qps, wallSec);
        }
    }
}
