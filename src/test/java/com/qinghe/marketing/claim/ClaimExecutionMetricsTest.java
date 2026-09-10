package com.qinghe.marketing.claim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimExecutionMetricsTest {
    @Test
    void shouldExposeIdempotentHitsSeparatelyFromAcceptedRequests() {
        ClaimExecutionMetrics metrics = new ClaimExecutionMetrics();
        metrics.accepted();
        metrics.succeeded(2_000_000);
        metrics.idempotentHit();

        ClaimExecutionMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(1, snapshot.accepted());
        assertEquals(1, snapshot.succeeded());
        assertEquals(1, snapshot.idempotentHits());
        assertEquals(2_000_000, snapshot.durationNanos());
    }
}
