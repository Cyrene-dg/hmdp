package com.qinghe.marketing.redemption;

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
public class PosExecutionGuard {

    private final AsyncTaskExecutor executor;
    private final PosExecutionMetrics metrics;
    private final Semaphore inFlight;
    private final long timeoutMillis;

    public PosExecutionGuard(@Qualifier("qinghePosExecutor") AsyncTaskExecutor executor,
                             PosExecutionMetrics metrics,
                             @Value("${qinghe.pos.execution.max-in-flight:72}") int maxInFlight,
                             @Value("${qinghe.pos.execution.timeout-ms:3000}") long timeoutMillis) {
        if (maxInFlight <= 0 || timeoutMillis <= 0) {
            throw new IllegalArgumentException("invalid Qinghe POS admission configuration");
        }
        this.executor = executor;
        this.metrics = metrics;
        this.inFlight = new Semaphore(maxInFlight);
        this.timeoutMillis = timeoutMillis;
    }

    public <T> T execute(String operation, Callable<T> action) {
        if (!inFlight.tryAcquire()) {
            metrics.rejected(operation);
            throw new QingheBusinessException(QingheErrorCode.RATE_LIMITED,
                    "POS capacity is temporarily exhausted");
        }
        metrics.accepted(operation);
        Future<T> future;
        AtomicBoolean started = new AtomicBoolean();
        AtomicBoolean released = new AtomicBoolean();
        try {
            future = executor.submit(() -> {
                started.set(true);
                long startedNanos = System.nanoTime();
                try {
                    T result = action.call();
                    metrics.succeeded(operation, System.nanoTime() - startedNanos);
                    return result;
                } catch (Throwable failure) {
                    metrics.failed(operation, System.nanoTime() - startedNanos);
                    throw failure;
                } finally {
                    releaseOnce(released);
                }
            });
        } catch (TaskRejectedException rejected) {
            releaseOnce(released);
            metrics.rejected(operation);
            throw new QingheBusinessException(QingheErrorCode.RATE_LIMITED,
                    "POS executor rejected the request");
        }
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            if (future.cancel(true) && !started.get()) releaseOnce(released);
            throw new QingheBusinessException(QingheErrorCode.SYSTEM_BUSY,
                    "POS operation result is temporarily unknown");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new QingheBusinessException(QingheErrorCode.SYSTEM_BUSY,
                    "POS operation was interrupted");
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("POS execution failed", cause);
        }
    }

    public void idempotentHit(String operation) {
        metrics.idempotentHit(operation);
    }

    private void releaseOnce(AtomicBoolean released) {
        if (released.compareAndSet(false, true)) inFlight.release();
    }
}
