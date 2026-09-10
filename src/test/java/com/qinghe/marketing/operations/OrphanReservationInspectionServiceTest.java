package com.qinghe.marketing.operations;

import com.qinghe.marketing.claim.ClaimRequest;
import com.qinghe.marketing.claim.ClaimRequestRepository;
import com.qinghe.marketing.claim.ClaimReservationSnapshot;
import com.qinghe.marketing.claim.ClaimReservationStore;
import com.qinghe.marketing.claim.ClaimStatus;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrphanReservationInspectionServiceTest {
    private static final BusinessClock CLOCK = () -> Instant.parse("2026-09-10T06:00:00Z");

    @Test
    void shouldOnlyExposeTimedOutReservationsWithoutPersistedClaim() {
        ClaimReservationStore reservations = mock(ClaimReservationStore.class);
        ClaimRequestRepository claims = mock(ClaimRequestRepository.class);
        ClaimReservationSnapshot linked = snapshot("RSV-LINKED", 1);
        ClaimReservationSnapshot orphan = snapshot("RSV-ORPHAN", 2);
        when(reservations.findPendingBefore(10L, CLOCK.dateTime().minusSeconds(120), 101))
                .thenReturn(Arrays.asList(linked, orphan));
        when(claims.findByReservationId("RSV-LINKED"))
                .thenReturn(Optional.of(new ClaimRequest(1L, "CLM-LINKED", "REQ-1", "digest",
                        10L, 1L, "CYCLE-1", "RSV-LINKED", ClaimStatus.PROCESSING, null, 0,
                        CLOCK.dateTime(), CLOCK.dateTime())));
        when(claims.findByReservationId("RSV-ORPHAN")).thenReturn(Optional.empty());
        OrphanReservationInspectionService service = new OrphanReservationInspectionService(
                reservations, claims, CLOCK, 120);

        OrphanReservationInspection result = service.inspect(10L, 100);

        assertEquals(2, result.getScanned());
        assertFalse(result.isTruncated());
        assertEquals(1, result.getItems().size());
        assertEquals("RSV-ORPHAN", result.getItems().get(0).getBusinessId());
    }

    @Test
    void shouldRejectUnboundedInspection() {
        OrphanReservationInspectionService service = new OrphanReservationInspectionService(
                mock(ClaimReservationStore.class), mock(ClaimRequestRepository.class), CLOCK, 120);

        QingheBusinessException failure = assertThrows(QingheBusinessException.class,
                () -> service.inspect(10L, 501));

        assertEquals(QingheErrorCode.INVALID_ARGUMENT, failure.errorCode());
    }

    private static ClaimReservationSnapshot snapshot(String reservationId, long memberId) {
        return new ClaimReservationSnapshot(10L, memberId, "REQ-" + memberId,
                reservationId, CLOCK.dateTime().minusMinutes(5));
    }
}
