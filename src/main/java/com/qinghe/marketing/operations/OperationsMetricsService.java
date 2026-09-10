package com.qinghe.marketing.operations;

import com.qinghe.marketing.claim.ClaimExecutionMetrics;
import com.qinghe.marketing.reconciliation.ReconciliationExecutionMetrics;
import com.qinghe.marketing.redemption.PosExecutionMetrics;
import com.qinghe.marketing.shared.clock.BusinessClock;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OperationsMetricsService {
    private static final String[] POS_OPERATIONS = {"verify", "redeem", "query", "reversal"};
    private final ClaimExecutionMetrics claimMetrics;
    private final PosExecutionMetrics posMetrics;
    private final ReconciliationExecutionMetrics reconciliationMetrics;
    private final OperationalGaugeRepository gauges;
    private final BusinessClock clock;

    public OperationsMetricsService(ClaimExecutionMetrics claimMetrics,
                                    PosExecutionMetrics posMetrics,
                                    ReconciliationExecutionMetrics reconciliationMetrics,
                                    OperationalGaugeRepository gauges,
                                    BusinessClock clock) {
        this.claimMetrics = claimMetrics;
        this.posMetrics = posMetrics;
        this.reconciliationMetrics = reconciliationMetrics;
        this.gauges = gauges;
        this.clock = clock;
    }

    public OperationsMetricsSnapshot snapshot() {
        Map<String, Map<String, Long>> pos = new LinkedHashMap<String, Map<String, Long>>();
        Arrays.stream(POS_OPERATIONS).forEach(operation ->
                pos.put(operation, values(posMetrics.snapshot(operation))));
        return new OperationsMetricsSnapshot(clock.dateTime(),
                "local-process counters plus MySQL state snapshot; counters reset on restart",
                values(claimMetrics.snapshot()), pos, reconciliationMetrics.snapshot(),
                gauges.snapshot());
    }

    private static Map<String, Long> values(ClaimExecutionMetrics.Snapshot snapshot) {
        Map<String, Long> values = new LinkedHashMap<String, Long>();
        values.put("accepted", snapshot.accepted());
        values.put("rejected", snapshot.rejected());
        values.put("succeeded", snapshot.succeeded());
        values.put("failed", snapshot.failed());
        values.put("idempotentHits", snapshot.idempotentHits());
        values.put("durationNanos", snapshot.durationNanos());
        values.put("averageLatencyMicros", averageMicros(snapshot.succeeded(), snapshot.failed(),
                snapshot.durationNanos()));
        return values;
    }

    private static Map<String, Long> values(PosExecutionMetrics.Snapshot snapshot) {
        Map<String, Long> values = new LinkedHashMap<String, Long>();
        values.put("accepted", snapshot.accepted());
        values.put("rejected", snapshot.rejected());
        values.put("succeeded", snapshot.succeeded());
        values.put("failed", snapshot.failed());
        values.put("idempotentHits", snapshot.idempotentHits());
        values.put("durationNanos", snapshot.durationNanos());
        values.put("averageLatencyMicros", averageMicros(snapshot.succeeded(), snapshot.failed(),
                snapshot.durationNanos()));
        return values;
    }

    private static long averageMicros(long succeeded, long failed, long durationNanos) {
        long completed = succeeded + failed;
        return completed == 0 ? 0 : durationNanos / completed / 1000;
    }
}
