package com.qinghe.marketing.claim;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class ClaimExecutionMetrics {

    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong idempotentHits = new AtomicLong();
    private final AtomicLong durationNanos = new AtomicLong();

    void accepted() { accepted.incrementAndGet(); }
    void rejected() { rejected.incrementAndGet(); }
    void succeeded(long duration) { succeeded.incrementAndGet(); durationNanos.addAndGet(duration); }
    void failed(long duration) { failed.incrementAndGet(); durationNanos.addAndGet(duration); }
    void idempotentHit() { idempotentHits.incrementAndGet(); }

    public Snapshot snapshot() {
        return new Snapshot(accepted.get(), rejected.get(), succeeded.get(), failed.get(),
                idempotentHits.get(), durationNanos.get());
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
