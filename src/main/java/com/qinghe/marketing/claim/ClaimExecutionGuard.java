package com.qinghe.marketing.claim;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ClaimExecutionGuard {

    private final AsyncTaskExecutor executor;
    private final ClaimExecutionMetrics metrics;
    private final Semaphore inFlight;
    private final long timeoutMillis;

    public ClaimExecutionGuard(@Qualifier("qingheClaimExecutor") AsyncTaskExecutor executor,
                               ClaimExecutionMetrics metrics,
                               @Value("${qinghe.claim.execution.max-in-flight:144}") int maxInFlight,
                               @Value("${qinghe.claim.execution.timeout-ms:3000}") long timeoutMillis) {
        if (maxInFlight <= 0 || timeoutMillis <= 0) {
            throw new IllegalArgumentException("invalid Qinghe claim admission configuration");
        }
        this.executor = executor;
        this.metrics = metrics;
        this.inFlight = new Semaphore(maxInFlight);
        this.timeoutMillis = timeoutMillis;
    }

    public <T> T execute(Callable<T> action) {
        if (!inFlight.tryAcquire()) {
            metrics.rejected();
            throw unavailable("claim capacity is temporarily exhausted");
        }
        metrics.accepted();
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean released = new AtomicBoolean();
        Future<T> future;
        try {
            future = executor.submit(() -> {
                started.set(true);
                long startedNanos = System.nanoTime();
                try {
                    T result = action.call();
                    metrics.succeeded(System.nanoTime() - startedNanos);
                    return result;
                } catch (Throwable failure) {
                    metrics.failed(System.nanoTime() - startedNanos);
                    throw failure;
                } finally {
                    releaseOnce(released);
                }
            });
        } catch (TaskRejectedException rejected) {
            releaseOnce(released);
            metrics.rejected();
            throw unavailable("claim executor rejected the request");
        }
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            if (future.cancel(true) && !started.get()) releaseOnce(released);
            throw unavailable("claim acceptance result is temporarily unknown");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable("claim acceptance was interrupted");
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("claim execution failed", cause);
        }
    }

    public void idempotentHit() {
        metrics.idempotentHit();
    }

    private void releaseOnce(AtomicBoolean released) {
        if (released.compareAndSet(false, true)) inFlight.release();
    }

    private static QingheBusinessException unavailable(String message) {
        return new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE, message);
    }
}
