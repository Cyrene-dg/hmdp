package com.qinghe.marketing.claim;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimReservationRecoveryServiceTest {

    private static final BusinessClock CLOCK = () -> Instant.parse("2026-09-09T08:00:00Z");

    @Test
    void shouldLinkPersistedReservationAndCompensateOnlyMissingClaim() {
        ClaimRequestRepository claims = mock(ClaimRequestRepository.class);
        ClaimReservationStore reservations = mock(ClaimReservationStore.class);
        ClaimReservationSnapshot linked = new ClaimReservationSnapshot(
                10L, 20L, "request-001", "reservation-1", CLOCK.dateTime().minusMinutes(5));
        ClaimReservationSnapshot orphan = new ClaimReservationSnapshot(
                10L, 21L, "request-002", "reservation-2", CLOCK.dateTime().minusMinutes(4));
        when(reservations.findPendingBefore(10L, CLOCK.dateTime().minusMinutes(2), 50))
                .thenReturn(Arrays.asList(linked, orphan));
        when(claims.findByReservationId("reservation-1"))
                .thenReturn(Optional.of(claim("reservation-1")));
        when(claims.findByReservationId("reservation-2")).thenReturn(Optional.empty());
        ClaimReservationRecoveryService service = new ClaimReservationRecoveryService(
                claims, reservations, CLOCK);

        ClaimReservationRecoveryService.RecoverySummary summary = service.recoverCampaign(
                10L, Duration.ofMinutes(2), 50);

        assertEquals(2, summary.scanned());
        assertEquals(1, summary.linked());
        assertEquals(1, summary.compensated());
        verify(reservations).markPersisted(10L, "reservation-1");
        verify(reservations).compensate(10L, 21L, "request-002",
                "reservation-2", "ORPHAN_RESERVATION");
    }

    private static ClaimRequest claim(String reservationId) {
        return new ClaimRequest(30L, "CLM-1", "request-001", repeat('a', 64),
                10L, 20L, "CAMPAIGN:10", reservationId, ClaimStatus.PROCESSING,
                null, 0L, CLOCK.dateTime(), CLOCK.dateTime());
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
