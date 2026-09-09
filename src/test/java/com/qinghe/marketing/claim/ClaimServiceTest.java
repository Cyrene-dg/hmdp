package com.qinghe.marketing.claim;

import com.qinghe.marketing.campaign.Campaign;
import com.qinghe.marketing.campaign.CampaignRepository;
import com.qinghe.marketing.campaign.CampaignStatus;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T08:00:00Z");
    private static final OffsetDateTime CLIENT_TIME = OffsetDateTime.parse("2026-09-09T16:00:00+08:00");
    private static final BusinessClock CLOCK = () -> NOW;

    @Test
    void shouldReserveThenPersistProcessingClaimAndOutbox() {
        Fixture fixture = new Fixture();
        when(fixture.campaigns.findByCampaignNo("CAM-1")).thenReturn(Optional.of(activeCampaign()));
        when(fixture.reservations.reserve(any(ClaimReservationCommand.class)))
                .thenAnswer(invocation -> {
                    ClaimReservationCommand command = invocation.getArgument(0);
                    return new ClaimReservationResult(ClaimReservationResult.Outcome.RESERVED,
                            command.reservationId(), command.claimNo(), command.eventId());
                });
        when(fixture.transactions.accept(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> claim(invocation.getArgument(5), invocation.getArgument(2),
                        invocation.getArgument(3), invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(4)));

        ClaimSubmissionResult result = fixture.service.submit("CAM-1", 20L,
                "request-001", CLIENT_TIME);

        assertFalse(result.replay());
        assertEquals(ClaimStatus.PROCESSING, result.claimRequest().status());
        ArgumentCaptor<ClaimReservationCommand> command =
                ArgumentCaptor.forClass(ClaimReservationCommand.class);
        verify(fixture.reservations).reserve(command.capture());
        assertEquals("request-001", command.getValue().requestId());
        verify(fixture.reservations).markPersisted(10L, command.getValue().reservationId());
    }

    @Test
    void shouldReturnOriginalClaimForSameRequestAndRejectChangedDigest() {
        Fixture fixture = new Fixture();
        String digest = ClaimRequestDigest.calculate("CAM-1", CLIENT_TIME);
        ClaimRequest existing = claim("CLM-1", "request-001", digest, 10L, 20L, "reservation-1");
        when(fixture.claims.findByMemberAndRequestId(20L, "request-001"))
                .thenReturn(Optional.of(existing));

        ClaimSubmissionResult replay = fixture.service.submit("CAM-1", 20L,
                "request-001", CLIENT_TIME);
        assertTrue(replay.replay());
        assertEquals("CLM-1", replay.claimRequest().claimNo());
        verify(fixture.reservations, never()).reserve(any(ClaimReservationCommand.class));

        QingheBusinessException conflict = assertThrows(QingheBusinessException.class,
                () -> fixture.service.submit("CAM-1", 20L, "request-001",
                        CLIENT_TIME.plusSeconds(1)));
        assertEquals(QingheErrorCode.REQUEST_CONFLICT, conflict.errorCode());
    }

    @Test
    void shouldMapSoldOutWithoutWritingDatabase() {
        Fixture fixture = new Fixture();
        when(fixture.campaigns.findByCampaignNo("CAM-1")).thenReturn(Optional.of(activeCampaign()));
        when(fixture.reservations.reserve(any(ClaimReservationCommand.class)))
                .thenReturn(new ClaimReservationResult(ClaimReservationResult.Outcome.SOLD_OUT,
                        "", "", ""));

        QingheBusinessException soldOut = assertThrows(QingheBusinessException.class,
                () -> fixture.service.submit("CAM-1", 20L, "request-001", CLIENT_TIME));

        assertEquals(QingheErrorCode.SOLD_OUT, soldOut.errorCode());
        verify(fixture.transactions, never()).accept(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void shouldCompensateReservationWhenLocalTransactionFails() {
        Fixture fixture = new Fixture();
        when(fixture.campaigns.findByCampaignNo("CAM-1")).thenReturn(Optional.of(activeCampaign()));
        when(fixture.reservations.reserve(any(ClaimReservationCommand.class)))
                .thenAnswer(invocation -> {
                    ClaimReservationCommand command = invocation.getArgument(0);
                    return new ClaimReservationResult(ClaimReservationResult.Outcome.RESERVED,
                            command.reservationId(), command.claimNo(), command.eventId());
                });
        when(fixture.transactions.accept(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        QingheBusinessException unavailable = assertThrows(QingheBusinessException.class,
                () -> fixture.service.submit("CAM-1", 20L, "request-001", CLIENT_TIME));

        assertEquals(QingheErrorCode.TEMPORARY_UNAVAILABLE, unavailable.errorCode());
        ArgumentCaptor<ClaimReservationCommand> command =
                ArgumentCaptor.forClass(ClaimReservationCommand.class);
        verify(fixture.reservations).reserve(command.capture());
        verify(fixture.reservations).compensate(eq(10L), eq(20L), eq("request-001"),
                eq(command.getValue().reservationId()), eq("CLAIM_PERSISTENCE_FAILED"));
    }

    @Test
    void shouldRejectCampaignOutsideActiveWindowBeforeRedis() {
        Fixture fixture = new Fixture();
        Campaign scheduled = campaign(CampaignStatus.SCHEDULED,
                CLOCK.dateTime().plusMinutes(1), CLOCK.dateTime().plusDays(1));
        when(fixture.campaigns.findByCampaignNo("CAM-1")).thenReturn(Optional.of(scheduled));

        QingheBusinessException inactive = assertThrows(QingheBusinessException.class,
                () -> fixture.service.submit("CAM-1", 20L, "request-001", CLIENT_TIME));

        assertEquals(QingheErrorCode.CAMPAIGN_NOT_ACTIVE, inactive.errorCode());
        verify(fixture.reservations, never()).reserve(any(ClaimReservationCommand.class));
    }

    private static Campaign activeCampaign() {
        return campaign(CampaignStatus.ACTIVE,
                CLOCK.dateTime().minusHours(1), CLOCK.dateTime().plusDays(1));
    }

    private static Campaign campaign(CampaignStatus status, LocalDateTime begin, LocalDateTime end) {
        return new Campaign(10L, "CAM-1", 1L, "回馈活动", "试点", status,
                begin, end, 1, 300L, 1L, 3L, "MKT-001");
    }

    private static ClaimRequest claim(String claimNo, String requestId, String digest,
                                      long campaignId, long memberId, String reservationId) {
        return new ClaimRequest(30L, claimNo, requestId, digest, campaignId, memberId,
                "CAMPAIGN:" + campaignId, reservationId, ClaimStatus.PROCESSING,
                null, 0L, CLOCK.dateTime(), CLOCK.dateTime());
    }

    private static final class Fixture {
        private final CampaignRepository campaigns = mock(CampaignRepository.class);
        private final ClaimRequestRepository claims = mock(ClaimRequestRepository.class);
        private final ClaimReservationStore reservations = mock(ClaimReservationStore.class);
        private final ClaimAcceptanceTransactionService transactions =
                mock(ClaimAcceptanceTransactionService.class);
        private final ClaimService service = new ClaimService(campaigns, claims, reservations,
                transactions, new BusinessIdGenerator(CLOCK), CLOCK);
    }
}
