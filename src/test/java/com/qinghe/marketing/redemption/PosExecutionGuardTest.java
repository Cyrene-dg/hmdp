package com.qinghe.marketing.redemption;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PosExecutionGuardTest {

    private ThreadPoolTaskExecutor executor;
    private ExecutorService caller;

    @AfterEach
    void tearDown() {
        if (caller != null) caller.shutdownNow();
        if (executor != null) executor.shutdown();
    }

    @Test
    void shouldRejectWhenIndependentPosCapacityIsExhausted() throws Exception {
        executor = executor(1, 1);
        PosExecutionMetrics metrics = new PosExecutionMetrics();
        PosExecutionGuard guard = new PosExecutionGuard(executor, metrics, 1, 2000);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        caller = Executors.newSingleThreadExecutor();
        Future<String> first = caller.submit(() -> guard.execute("redeem", () -> {
            started.countDown();
            release.await();
            return "ok";
        }));
        assertTrue(started.await(1, java.util.concurrent.TimeUnit.SECONDS));

        QingheBusinessException rejected = assertThrows(QingheBusinessException.class,
                () -> guard.execute("redeem", () -> "second"));
        assertEquals(QingheErrorCode.RATE_LIMITED, rejected.errorCode());
        release.countDown();
        assertEquals("ok", first.get());
        assertEquals(1, metrics.snapshot("redeem").rejected());
        assertEquals(1, metrics.snapshot("redeem").succeeded());
        guard.idempotentHit("redeem");
        assertEquals(1, metrics.snapshot("redeem").idempotentHits());
    }

    @Test
    void shouldReturnUnknownResultOnTimeout() {
        executor = executor(1, 1);
        PosExecutionMetrics metrics = new PosExecutionMetrics();
        PosExecutionGuard guard = new PosExecutionGuard(executor, metrics, 1, 20);

        QingheBusinessException failure = assertThrows(QingheBusinessException.class,
                () -> guard.execute("query", () -> {
                    Thread.sleep(500);
                    return "late";
                }));

        assertEquals(QingheErrorCode.SYSTEM_BUSY, failure.errorCode());
    }

    @Test
    void shouldReleaseAdmissionPermitWhenQueuedTaskIsCancelledBeforeStarting() throws Exception {
        executor = executor(1, 2);
        PosExecutionMetrics metrics = new PosExecutionMetrics();
        PosExecutionGuard guard = new PosExecutionGuard(executor, metrics, 2, 30);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        caller = Executors.newSingleThreadExecutor();
        Future<Object> first = caller.submit(() -> {
            try {
                return guard.execute("redeem", () -> {
                    started.countDown();
                    while (release.getCount() > 0) {
                        try {
                            release.await();
                        } catch (InterruptedException ignored) {
                            // Simulate a database call that cannot be cancelled immediately.
                        }
                    }
                    return "first";
                });
            } catch (QingheBusinessException timeout) {
                return timeout;
            }
        });
        assertTrue(started.await(1, java.util.concurrent.TimeUnit.SECONDS));
        assertThrows(QingheBusinessException.class,
                () -> guard.execute("redeem", () -> "queued"));

        QingheBusinessException third = assertThrows(QingheBusinessException.class,
                () -> guard.execute("redeem", () -> "third"));
        assertEquals(QingheErrorCode.SYSTEM_BUSY, third.errorCode());
        release.countDown();
        first.get();
    }

    private static ThreadPoolTaskExecutor executor(int coreSize, int queueCapacity) {
        ThreadPoolTaskExecutor result = new ThreadPoolTaskExecutor();
        result.setCorePoolSize(coreSize);
        result.setMaxPoolSize(coreSize);
        result.setQueueCapacity(queueCapacity);
        result.setThreadNamePrefix("pos-test-");
        result.initialize();
        return result;
    }
}
