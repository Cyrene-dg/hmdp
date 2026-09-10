package com.qinghe.marketing.operations;

import com.qinghe.marketing.claim.ClaimExecutionMetrics;
import com.qinghe.marketing.reconciliation.ReconciliationExecutionMetrics;
import com.qinghe.marketing.redemption.PosExecutionMetrics;
import com.qinghe.marketing.shared.clock.BusinessClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationsMetricsServiceTest {
    @Test
    void shouldDistinguishProcessCountersFromPersistentGauges() {
        OperationalGaugeRepository gauges = mock(OperationalGaugeRepository.class);
        when(gauges.snapshot()).thenReturn(Collections.singletonMap("outbox.status.DEAD", 2L));
        BusinessClock clock = () -> Instant.parse("2026-09-10T06:30:00Z");
        OperationsMetricsService service = new OperationsMetricsService(
                new ClaimExecutionMetrics(), new PosExecutionMetrics(),
                new ReconciliationExecutionMetrics(), gauges, clock);

        OperationsMetricsSnapshot snapshot = service.snapshot();

        assertEquals(clock.dateTime(), snapshot.getGeneratedAt());
        assertEquals(2L, snapshot.getPersistentGauges().get("outbox.status.DEAD"));
        assertTrue(snapshot.getScope().contains("reset on restart"));
        assertTrue(snapshot.getPos().containsKey("reversal"));
        assertEquals(0L, snapshot.getClaim().get("averageLatencyMicros"));
    }
}
