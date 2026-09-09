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

class InventoryAdjustmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T08:00:00Z");
    private final BusinessClock clock = () -> NOW;

    @Test
    void shouldAllowOnlyPositiveIncreaseForScheduledOrActiveCampaign() {
        InventoryAdjustmentRepository adjustments = mock(InventoryAdjustmentRepository.class);
        CampaignRepository campaigns = mock(CampaignRepository.class);
        InventoryAdjustmentService service = service(adjustments, campaigns, mock(CampaignStockCache.class));

        assertEquals(QingheErrorCode.INVALID_ARGUMENT, assertThrows(QingheBusinessException.class,
                () -> service.create("CAM-1", 0L, "减少", 2L, "MKT-001")).errorCode());

        when(campaigns.findByCampaignNo("CAM-1")).thenReturn(Optional.of(campaign(CampaignStatus.ENDED)));
        assertEquals(QingheErrorCode.CAMPAIGN_STATE_CONFLICT,
                assertThrows(QingheBusinessException.class,
                        () -> service.create("CAM-1", 100L, "追加", 2L, "MKT-001")).errorCode());
        verify(adjustments, never()).create(any(), eq(10L), eq(100L), any(), any(), any());
    }

    @Test
    void shouldApplyApprovedAdjustmentAndSynchronizeRedisByAdjustmentNumber() {
        InventoryAdjustmentRepository adjustments = mock(InventoryAdjustmentRepository.class);
        CampaignRepository campaigns = mock(CampaignRepository.class);
        CampaignStockCache cache = mock(CampaignStockCache.class);
        InventoryAdjustment pending = adjustment(InventoryAdjustmentStatus.PENDING_APPROVAL, 1L);
        InventoryAdjustment applied = adjustment(InventoryAdjustmentStatus.APPLIED, 3L);
        when(adjustments.findByAdjustmentNo("IAD-1")).thenReturn(Optional.of(pending));
        when(adjustments.approveAndApply(eq(20L), eq(1L), eq("OPS-002"), eq("同意"),
                any(LocalDateTime.class)))
                .thenReturn(new InventoryAdjustmentResult(applied, 1000L, 1200L));
        when(cache.applyApprovedIncrease(10L, "IAD-1", 200L)).thenReturn(1200L);
        InventoryAdjustmentService service = service(adjustments, campaigns, cache);

        InventoryAdjustmentResult result = service.review(
                "IAD-1", ReviewDecision.APPROVE, 1L, "OPS-002", "同意");

        assertEquals(1000L, result.beforeTotalStock());
        assertEquals(1200L, result.afterTotalStock());
        assertEquals(InventoryAdjustmentStatus.APPLIED, result.adjustment().status());
        verify(cache).applyApprovedIncrease(10L, "IAD-1", 200L);
    }

    @Test
    void shouldRejectSelfApproval() {
        InventoryAdjustmentRepository adjustments = mock(InventoryAdjustmentRepository.class);
        when(adjustments.findByAdjustmentNo("IAD-1"))
                .thenReturn(Optional.of(adjustment(InventoryAdjustmentStatus.PENDING_APPROVAL, 1L)));
        InventoryAdjustmentService service = service(
                adjustments, mock(CampaignRepository.class), mock(CampaignStockCache.class));

        QingheBusinessException exception = assertThrows(QingheBusinessException.class,
                () -> service.review("IAD-1", ReviewDecision.APPROVE, 1L, "MKT-001", "同意"));

        assertEquals(QingheErrorCode.SELF_APPROVAL_NOT_ALLOWED, exception.errorCode());
    }

    private InventoryAdjustmentService service(InventoryAdjustmentRepository adjustments,
                                               CampaignRepository campaigns, CampaignStockCache cache) {
        return new InventoryAdjustmentService(adjustments, campaigns, cache,
                new BusinessIdGenerator(clock), clock);
    }

    private static Campaign campaign(CampaignStatus status) {
        LocalDateTime begin = LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC);
        return new Campaign(10L, "CAM-1", 1L, "活动", null, status,
                begin, begin.plusDays(7), 1, null, 1L, 2L, "MKT-001");
    }

    private static InventoryAdjustment adjustment(InventoryAdjustmentStatus status, long version) {
        return new InventoryAdjustment(20L, "IAD-1", 10L, 200L, status,
                "活动效果好", "MKT-001", status == InventoryAdjustmentStatus.APPLIED ? "OPS-002" : null,
                version);
    }
}
