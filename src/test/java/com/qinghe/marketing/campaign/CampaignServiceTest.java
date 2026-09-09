package com.qinghe.marketing.campaign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.store.StoreOwnershipType;
import com.qinghe.marketing.store.StoreRecord;
import com.qinghe.marketing.store.StoreRepository;
import com.qinghe.marketing.store.StoreStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampaignServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T08:00:00Z");
    private static final LocalDateTime BEGIN = LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC);
    private static final LocalDateTime END = BEGIN.plusDays(7);
    private final BusinessClock clock = () -> NOW;

    @Test
    void shouldCreateDirectAndUniformFranchiseSubsidySnapshots() {
        CampaignRepository campaigns = mock(CampaignRepository.class);
        BenefitTemplateRepository templates = mock(BenefitTemplateRepository.class);
        StoreRepository stores = mock(StoreRepository.class);
        when(templates.findByTemplateNo("TPL-1")).thenReturn(Optional.of(template()));
        when(stores.findByExternalStoreCode("QH001")).thenReturn(Optional.of(store(
                1L, "QH001", StoreOwnershipType.DIRECT, StoreStatus.ACTIVE)));
        when(stores.findByExternalStoreCode("QH006")).thenReturn(Optional.of(store(
                6L, "QH006", StoreOwnershipType.FRANCHISE, StoreStatus.ACTIVE)));
        when(stores.findByExternalStoreCode("QH007")).thenReturn(Optional.of(store(
                7L, "QH007", StoreOwnershipType.FRANCHISE, StoreStatus.ACTIVE)));
        when(campaigns.createDraft(anyString(), eq(1L), any(CampaignDraftCommand.class),
                eq("MKT-001"), anyList(), any(LocalDateTime.class))).thenReturn(campaign(CampaignStatus.DRAFT, 0));
        CampaignService service = service(campaigns, templates, stores);

        service.createDraft(new CampaignDraftCommand("回馈活动", "试点", "TPL-1", BEGIN, END,
                1000, 1, Arrays.asList("QH001", "QH006", "QH007"), 350L), "MKT-001");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CampaignStoreSnapshot>> snapshots = ArgumentCaptor.forClass(List.class);
        verify(campaigns).createDraft(anyString(), eq(1L), any(CampaignDraftCommand.class),
                eq("MKT-001"), snapshots.capture(), any(LocalDateTime.class));
        assertEquals(0L, snapshots.getValue().get(0).subsidyFen());
        assertEquals(350L, snapshots.getValue().get(1).subsidyFen());
        assertEquals(350L, snapshots.getValue().get(2).subsidyFen());
    }

    @Test
    void shouldRejectCampaignWithoutStoresOrWithDisabledStore() {
        CampaignRepository campaigns = mock(CampaignRepository.class);
        BenefitTemplateRepository templates = mock(BenefitTemplateRepository.class);
        StoreRepository stores = mock(StoreRepository.class);
        when(templates.findByTemplateNo("TPL-1")).thenReturn(Optional.of(template()));
        CampaignService service = service(campaigns, templates, stores);

        assertEquals(QingheErrorCode.CAMPAIGN_INCOMPLETE, assertThrows(QingheBusinessException.class,
                () -> service.createDraft(new CampaignDraftCommand("活动", null, "TPL-1", BEGIN, END,
                        10, 1, Collections.<String>emptyList(), null), "MKT-001")).errorCode());

        when(stores.findByExternalStoreCode("QH001")).thenReturn(Optional.of(store(
                1L, "QH001", StoreOwnershipType.DIRECT, StoreStatus.DISABLED)));
        assertEquals(QingheErrorCode.STORE_NOT_ELIGIBLE, assertThrows(QingheBusinessException.class,
                () -> service.createDraft(new CampaignDraftCommand("活动", null, "TPL-1", BEGIN, END,
                        10, 1, Collections.singletonList("QH001"), null), "MKT-001")).errorCode());
    }

    @Test
    void shouldFreezeTemplateAndInitializeStockWhenApproved() {
        CampaignRepository campaigns = mock(CampaignRepository.class);
        BenefitTemplateRepository templates = mock(BenefitTemplateRepository.class);
        StoreRepository stores = mock(StoreRepository.class);
        CampaignApprovalTransactionService approval = mock(CampaignApprovalTransactionService.class);
        CampaignStockCache cache = mock(CampaignStockCache.class);
        Campaign pending = campaign(CampaignStatus.PENDING_APPROVAL, 1);
        Campaign scheduled = campaign(CampaignStatus.SCHEDULED, 2);
        when(campaigns.findByCampaignNo("CAM-1")).thenReturn(Optional.of(pending));
        when(campaigns.requireInventory(10L)).thenReturn(new CampaignInventory(10L, 1000L, 0));
        CampaignStoreSnapshot storeSnapshot = new CampaignStoreSnapshot(
                1L, "QH001", StoreOwnershipType.DIRECT, 0L, "PARTICIPATING", 1L);
        when(campaigns.findStores(10L)).thenReturn(Collections.singletonList(storeSnapshot));
        when(stores.findById(1L)).thenReturn(Optional.of(store(
                1L, "QH001", StoreOwnershipType.DIRECT, StoreStatus.ACTIVE)));
        when(templates.findById(1L)).thenReturn(Optional.of(template()));
        when(approval.approve(eq(pending), eq(1L), eq("OPS-002"), eq("同意"),
                any(CampaignPublicationSnapshot.class))).thenReturn(scheduled);
        CampaignService service = new CampaignService(campaigns, templates,
                new BenefitTemplateSnapshotCodec(new ObjectMapper()), stores, approval, cache,
                new BusinessIdGenerator(clock), clock);

        Campaign result = service.review("CAM-1", ReviewDecision.APPROVE, 1L, "OPS-002", "同意");

        assertEquals(CampaignStatus.SCHEDULED, result.status());
        ArgumentCaptor<CampaignPublicationSnapshot> snapshot =
                ArgumentCaptor.forClass(CampaignPublicationSnapshot.class);
        verify(approval).approve(eq(pending), eq(1L), eq("OPS-002"), eq("同意"), snapshot.capture());
        assertEquals(1000L, snapshot.getValue().initialStock());
        assertEquals("免费指定饮品", new ObjectMapper().convertValue(
                read(snapshot.getValue().templateSnapshotJson()).get("title"), String.class));
        verify(cache).initialize(10L, 1000L);
    }

    @Test
    void shouldNotAllowPublishedCampaignDraftOverwrite() {
        CampaignRepository campaigns = mock(CampaignRepository.class);
        BenefitTemplateRepository templates = mock(BenefitTemplateRepository.class);
        StoreRepository stores = mock(StoreRepository.class);
        when(campaigns.findByCampaignNo("CAM-1"))
                .thenReturn(Optional.of(campaign(CampaignStatus.SCHEDULED, 2L)));
        CampaignService service = service(campaigns, templates, stores);
        CampaignDraftCommand command = new CampaignDraftCommand("改名", null, "TPL-1", BEGIN, END,
                2000L, 1, Collections.singletonList("QH001"), null);

        QingheBusinessException exception = assertThrows(QingheBusinessException.class,
                () -> service.updateDraft("CAM-1", command, 2L, "MKT-001"));

        assertEquals(QingheErrorCode.CAMPAIGN_STATE_CONFLICT, exception.errorCode());
    }

    @Test
    void shouldAllowOwnerToReviseRejectedCampaignBackToDraft() {
        CampaignRepository campaigns = mock(CampaignRepository.class);
        BenefitTemplateRepository templates = mock(BenefitTemplateRepository.class);
        StoreRepository stores = mock(StoreRepository.class);
        Campaign rejected = campaign(CampaignStatus.REJECTED, 2L);
        Campaign revised = campaign(CampaignStatus.DRAFT, 3L);
        when(campaigns.findByCampaignNo("CAM-1")).thenReturn(Optional.of(rejected));
        when(templates.findByTemplateNo("TPL-1")).thenReturn(Optional.of(template()));
        when(stores.findByExternalStoreCode("QH001")).thenReturn(Optional.of(store(
                1L, "QH001", StoreOwnershipType.DIRECT, StoreStatus.ACTIVE)));
        when(campaigns.updateDraft(eq(10L), eq(1L), any(CampaignDraftCommand.class),
                eq("MKT-001"), anyList(), eq(2L), any(LocalDateTime.class))).thenReturn(revised);
        CampaignService service = service(campaigns, templates, stores);
        CampaignDraftCommand command = new CampaignDraftCommand("改后活动", "按意见修改", "TPL-1",
                BEGIN, END, 1000L, 1, Collections.singletonList("QH001"), null);

        Campaign result = service.updateDraft("CAM-1", command, 2L, "MKT-001");

        assertEquals(CampaignStatus.DRAFT, result.status());
        assertEquals(3L, result.version());
    }

    @Test
    void shouldAdvanceScheduledAndEndedStatesIdempotentlyThroughRepository() {
        CampaignRepository campaigns = mock(CampaignRepository.class);
        when(campaigns.activateScheduled(any(LocalDateTime.class))).thenReturn(2);
        when(campaigns.endActive(any(LocalDateTime.class))).thenReturn(1);
        CampaignService service = service(campaigns,
                mock(BenefitTemplateRepository.class), mock(StoreRepository.class));

        assertEquals(3, service.advanceTimeBasedStates());
        verify(campaigns).activateScheduled(any(LocalDateTime.class));
        verify(campaigns).endActive(any(LocalDateTime.class));
    }

    private CampaignService service(CampaignRepository campaigns,
                                    BenefitTemplateRepository templates, StoreRepository stores) {
        return new CampaignService(campaigns, templates,
                new BenefitTemplateSnapshotCodec(new ObjectMapper()), stores,
                mock(CampaignApprovalTransactionService.class), mock(CampaignStockCache.class),
                new BusinessIdGenerator(clock), clock);
    }

    private static com.fasterxml.jackson.databind.JsonNode read(String value) {
        try {
            return new ObjectMapper().readTree(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static BenefitTemplate template() {
        BenefitTemplateDraft draft = new BenefitTemplateDraft("饮品券", BenefitType.FREE_PRODUCT,
                "免费指定饮品", "试点门店", "DRINK-M-001", null,
                ValidityType.RELATIVE_DAYS, 7, null, null, "{\"allowStacking\":false}");
        return new BenefitTemplate(1L, "TPL-1", draft, BenefitTemplateStatus.ACTIVE, 0L);
    }

    private static StoreRecord store(long id, String code, StoreOwnershipType type, StoreStatus status) {
        return new StoreRecord(id, code, code, type, status, "POS-3.2", "STORE-1", 0L);
    }

    private static Campaign campaign(CampaignStatus status, long version) {
        return new Campaign(10L, "CAM-1", 1L, "回馈活动", "试点", status,
                BEGIN, END, 1, null, 1L, version, "MKT-001");
    }
}
