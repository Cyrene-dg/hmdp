package com.qinghe.marketing.redemption;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PosExecutionMetrics {

    private final ConcurrentHashMap<String, OperationCounters> operations =
            new ConcurrentHashMap<String, OperationCounters>();

    void accepted(String operation) { counters(operation).accepted.incrementAndGet(); }
    void rejected(String operation) { counters(operation).rejected.incrementAndGet(); }
    void succeeded(String operation, long durationNanos) {
        OperationCounters counters = counters(operation);
        counters.succeeded.incrementAndGet();
        counters.durationNanos.addAndGet(durationNanos);
    }
    void failed(String operation, long durationNanos) {
        OperationCounters counters = counters(operation);
        counters.failed.incrementAndGet();
        counters.durationNanos.addAndGet(durationNanos);
    }
    void idempotentHit(String operation) { counters(operation).idempotentHits.incrementAndGet(); }

    public Snapshot snapshot(String operation) {
        OperationCounters counters = counters(operation);
        return new Snapshot(counters.accepted.get(), counters.rejected.get(),
                counters.succeeded.get(), counters.failed.get(), counters.idempotentHits.get(),
                counters.durationNanos.get());
    }

    private OperationCounters counters(String operation) {
        return operations.computeIfAbsent(operation, ignored -> new OperationCounters());
    }

    private static final class OperationCounters {
        private final AtomicLong accepted = new AtomicLong();
        private final AtomicLong rejected = new AtomicLong();
        private final AtomicLong succeeded = new AtomicLong();
        private final AtomicLong failed = new AtomicLong();
        private final AtomicLong idempotentHits = new AtomicLong();
        private final AtomicLong durationNanos = new AtomicLong();
    }

    public static final class Snapshot {
        private final long accepted;
        private final long rejected;
        private final long succeeded;
        private final long failed;
        private final long idempotentHits;
        private final long durationNanos;

        Snapshot(long accepted, long rejected, long succeeded, long failed, long idempotentHits,
                 long durationNanos) {
            this.accepted = accepted; this.rejected = rejected; this.succeeded = succeeded;
            this.failed = failed; this.idempotentHits = idempotentHits;
            this.durationNanos = durationNanos;
        }
        public long accepted() { return accepted; }
        public long rejected() { return rejected; }
        public long succeeded() { return succeeded; }
        public long failed() { return failed; }
        public long idempotentHits() { return idempotentHits; }
        public long durationNanos() { return durationNanos; }
    }
}
