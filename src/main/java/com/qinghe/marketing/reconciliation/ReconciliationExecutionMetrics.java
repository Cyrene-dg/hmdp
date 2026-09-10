package com.qinghe.marketing.reconciliation;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ReconciliationExecutionMetrics {
    private final EnumMap<ReconciliationFileOutcome, AtomicLong> outcomes =
            new EnumMap<ReconciliationFileOutcome, AtomicLong>(ReconciliationFileOutcome.class);
    private final AtomicLong durationNanos = new AtomicLong();

    public ReconciliationExecutionMetrics() {
        for (ReconciliationFileOutcome outcome : ReconciliationFileOutcome.values()) {
            outcomes.put(outcome, new AtomicLong());
        }
    }

    void record(ReconciliationFileOutcome outcome, long elapsedNanos) {
        outcomes.get(outcome).incrementAndGet();
        durationNanos.addAndGet(Math.max(0, elapsedNanos));
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> snapshot = new LinkedHashMap<String, Long>();
        long processed = 0;
        for (Map.Entry<ReconciliationFileOutcome, AtomicLong> entry : outcomes.entrySet()) {
            long count = entry.getValue().get();
            snapshot.put(entry.getKey().name().toLowerCase(java.util.Locale.ROOT), count);
            processed += count;
        }
        snapshot.put("processed", processed);
        snapshot.put("durationNanos", durationNanos.get());
        snapshot.put("idempotentHits", outcomes.get(ReconciliationFileOutcome.DUPLICATE).get());
        return snapshot;
    }
}
