package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.claim.ClaimRequest;
import com.qinghe.marketing.claim.ClaimReservationStore;
import com.qinghe.marketing.claim.ClaimStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimFailureServiceTest {

    @Test
    void shouldFinishFailureOnlyAfterRedisCompensation() {
        ClaimFailureTransactionService transactions = mock(ClaimFailureTransactionService.class);
        ClaimReservationStore reservations = mock(ClaimReservationStore.class);
        ClaimRequest claim = claim(ClaimStatus.COMPENSATING);
        when(transactions.begin("CLM-1", "OUTBOX_DEAD"))
                .thenReturn(new CompensationClaim(claim, true));
        ClaimFailureService service = new ClaimFailureService(transactions, reservations);

        service.fail("EVT-1", "CLM-1", "OUTBOX_DEAD");

        verify(reservations).compensate(10L, 20L, "request-001", "RSV-1", "OUTBOX_DEAD");
        verify(transactions).complete(claim, "EVT-1", "OUTBOX_DEAD");
    }

    @Test
    void shouldLeaveClaimCompensatingWhenRedisIsUnavailable() {
        ClaimFailureTransactionService transactions = mock(ClaimFailureTransactionService.class);
        ClaimReservationStore reservations = mock(ClaimReservationStore.class);
        ClaimRequest claim = claim(ClaimStatus.COMPENSATING);
        when(transactions.begin("CLM-1", "OUTBOX_DEAD"))
                .thenReturn(new CompensationClaim(claim, true));
        doThrow(new IllegalStateException("redis down")).when(reservations)
                .compensate(10L, 20L, "request-001", "RSV-1", "OUTBOX_DEAD");
        ClaimFailureService service = new ClaimFailureService(transactions, reservations);

        assertThrows(IllegalStateException.class,
                () -> service.fail("EVT-1", "CLM-1", "OUTBOX_DEAD"));
        verify(transactions, never()).complete(claim, "EVT-1", "OUTBOX_DEAD");
    }

    private static ClaimRequest claim(ClaimStatus status) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);
        return new ClaimRequest(30L, "CLM-1", "request-001", repeat('a', 64),
                10L, 20L, "CAMPAIGN:10", "RSV-1", status, "OUTBOX_DEAD",
                1L, now, now);
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }
}
