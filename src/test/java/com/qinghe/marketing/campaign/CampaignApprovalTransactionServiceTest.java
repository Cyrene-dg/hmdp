package com.qinghe.marketing.campaign;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignApprovalTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T08:00:00Z");
    private final BusinessClock clock = () -> NOW;

    @Test
    void shouldPersistImmutableSnapshotReviewAndStateTogether() {
        CampaignRepository repository = mock(CampaignRepository.class);
        Campaign pending = campaign(CampaignStatus.PENDING_APPROVAL, 1L, "MKT-001");
        Campaign scheduled = campaign(CampaignStatus.SCHEDULED, 2L, "MKT-001");
        CampaignPublicationSnapshot snapshot = snapshot();
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(pending));
        when(repository.findPublication(10L)).thenReturn(Optional.empty());
        when(repository.transition(eq(10L), eq(CampaignStatus.PENDING_APPROVAL),
                eq(CampaignStatus.SCHEDULED), eq(1L), eq("OPS-002"), any(LocalDateTime.class)))
                .thenReturn(scheduled);
        CampaignApprovalTransactionService service = new CampaignApprovalTransactionService(
                repository, new BusinessIdGenerator(clock), clock);

        Campaign result = service.approve(pending, 1L, "OPS-002", "同意", snapshot);

        assertEquals(CampaignStatus.SCHEDULED, result.status());
        verify(repository).savePublication(snapshot);
        verify(repository).saveReview(any(CampaignReview.class));
    }

    @Test
    void repeatedApprovalShouldReturnPublishedCampaignWithoutSecondSnapshot() {
        CampaignRepository repository = mock(CampaignRepository.class);
        Campaign scheduled = campaign(CampaignStatus.SCHEDULED, 2L, "MKT-001");
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(scheduled));
        when(repository.findPublication(10L)).thenReturn(Optional.of(snapshot()));
        CampaignApprovalTransactionService service = new CampaignApprovalTransactionService(
                repository, new BusinessIdGenerator(clock), clock);

        Campaign result = service.approve(scheduled, 1L, "OPS-002", "重试", snapshot());

        assertEquals(CampaignStatus.SCHEDULED, result.status());
        verify(repository, never()).savePublication(any(CampaignPublicationSnapshot.class));
        verify(repository, never()).saveReview(any(CampaignReview.class));
    }

    @Test
    void shouldRejectSelfApprovalAndStaleVersion() {
        CampaignRepository repository = mock(CampaignRepository.class);
        Campaign pending = campaign(CampaignStatus.PENDING_APPROVAL, 2L, "MKT-001");
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(pending));
        CampaignApprovalTransactionService service = new CampaignApprovalTransactionService(
                repository, new BusinessIdGenerator(clock), clock);

        assertEquals(QingheErrorCode.VERSION_CONFLICT, assertThrows(QingheBusinessException.class,
                () -> service.approve(pending, 1L, "OPS-002", "同意", snapshot())).errorCode());
        assertEquals(QingheErrorCode.SELF_APPROVAL_NOT_ALLOWED,
                assertThrows(QingheBusinessException.class,
                        () -> service.approve(pending, 2L, "MKT-001", "同意", snapshot())).errorCode());
    }

    private static Campaign campaign(CampaignStatus status, long version, String creator) {
        LocalDateTime begin = LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC);
        return new Campaign(10L, "CAM-1", 1L, "活动", null, status,
                begin, begin.plusDays(7), 1, null, 1L, version, creator);
    }

    private static CampaignPublicationSnapshot snapshot() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        return new CampaignPublicationSnapshot(10L, 1L, "{}", now.plusHours(1),
                now.plusDays(7), 1, 1000L, null, "OPS-002", now);
    }
}
