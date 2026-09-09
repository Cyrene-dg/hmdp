package com.qinghe.marketing.redemption;

import com.qinghe.marketing.claim.ClaimExecutionGuard;
import com.qinghe.marketing.claim.ClaimExecutionMetrics;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimPosIsolationTest {

    @Test
    void saturatedClaimAdmissionMustNotBorrowPosExecutionCapacity() throws Exception {
        ThreadPoolTaskExecutor claimExecutor = executor("claim-load-", 2, 2);
        ThreadPoolTaskExecutor posExecutor = executor("pos-load-", 2, 8);
        ExecutorService callers = Executors.newFixedThreadPool(6);
        CountDownLatch claimStarted = new CountDownLatch(2);
        CountDownLatch releaseClaims = new CountDownLatch(1);
        try {
            ClaimExecutionMetrics claimMetrics = new ClaimExecutionMetrics();
            ClaimExecutionGuard claimGuard = new ClaimExecutionGuard(
                    claimExecutor, claimMetrics, 2, 40);
            PosExecutionMetrics posMetrics = new PosExecutionMetrics();
            PosExecutionGuard posGuard = new PosExecutionGuard(posExecutor, posMetrics, 10, 1000);
            List<Future<Object>> claimLoad = new ArrayList<Future<Object>>();
            for (int index = 0; index < 2; index++) {
                claimLoad.add(callers.submit(() -> runUninterruptibleClaim(
                        claimGuard, claimStarted, releaseClaims)));
            }
            assertTrue(claimStarted.await(1, java.util.concurrent.TimeUnit.SECONDS));

            Object rejected = runClaim(claimGuard);
            assertTrue(rejected instanceof QingheBusinessException);
            for (int index = 0; index < 50; index++) {
                assertEquals("ok", posGuard.execute("verify", () -> "ok"));
            }
            assertEquals(50, posMetrics.snapshot("verify").succeeded());
            assertEquals(0, posMetrics.snapshot("verify").rejected());
            assertTrue(claimMetrics.snapshot().rejected() >= 1);
            releaseClaims.countDown();
            for (Future<Object> load : claimLoad) load.get();
        } finally {
            releaseClaims.countDown();
            callers.shutdownNow();
            claimExecutor.shutdown();
            posExecutor.shutdown();
        }
    }

    private static Object runUninterruptibleClaim(ClaimExecutionGuard guard,
                                                  CountDownLatch started,
                                                  CountDownLatch release) {
        try {
            return guard.execute(() -> {
                started.countDown();
                while (release.getCount() > 0) {
                    try {
                        release.await();
                    } catch (InterruptedException ignored) {
                        // Represent a dependency call that does not stop immediately on cancellation.
                    }
                }
                return "done";
            });
        } catch (QingheBusinessException timeout) {
            return timeout;
        }
    }

    private static Object runClaim(ClaimExecutionGuard guard) {
        try {
            return guard.execute(() -> "unexpected");
        } catch (QingheBusinessException rejected) {
            return rejected;
        }
    }

    private static ThreadPoolTaskExecutor executor(String prefix, int size, int queue) {
        ThreadPoolTaskExecutor result = new ThreadPoolTaskExecutor();
        result.setCorePoolSize(size);
        result.setMaxPoolSize(size);
        result.setQueueCapacity(queue);
        result.setThreadNamePrefix(prefix);
        result.initialize();
        return result;
    }
}
