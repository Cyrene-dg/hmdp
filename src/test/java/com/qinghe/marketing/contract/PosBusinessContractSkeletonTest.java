package com.qinghe.marketing.contract;

import com.qinghe.marketing.campaign.BenefitType;
import com.qinghe.marketing.campaign.CampaignRepository;
import com.qinghe.marketing.campaign.CampaignStoreSnapshot;
import com.qinghe.marketing.entitlement.AesGcmRightCodeProtector;
import com.qinghe.marketing.entitlement.EntitlementStatus;
import com.qinghe.marketing.entitlement.RightCodeProtector;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.redemption.CampaignStorePolicy;
import com.qinghe.marketing.redemption.PosEntitlementSnapshot;
import com.qinghe.marketing.redemption.PosRedemptionCommand;
import com.qinghe.marketing.redemption.PosRedemptionRequest;
import com.qinghe.marketing.redemption.PosRedemptionService;
import com.qinghe.marketing.redemption.PosRequestStatus;
import com.qinghe.marketing.redemption.Redemption;
import com.qinghe.marketing.redemption.RedemptionRepository;
import com.qinghe.marketing.redemption.RedemptionResult;
import com.qinghe.marketing.redemption.RedemptionTransactionService;
import com.qinghe.marketing.redemption.SubsidyCandidate;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import com.qinghe.marketing.store.StoreOwnershipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Executable WP-06 contract behaviors backed by the real redemption services. */
class PosBusinessContractSkeletonTest {

    private static final Instant NOW = Instant.parse("2026-09-09T08:00:00Z");
    private static final String DIRECT_CODE = "RIGHT-DIRECT-001";
    private static final String FRANCHISE_CODE = "RIGHT-FRANCHISE-001";

    private InMemoryRedemptionRepository repository;
    private PosRedemptionService service;
    private PosAuthenticatedStore directStore;
    private PosAuthenticatedStore franchiseStore;

    @BeforeEach
    void setUp() {
        BusinessClock clock = () -> NOW;
        RightCodeProtector protector = new AesGcmRightCodeProtector("");
        repository = new InMemoryRedemptionRepository();
        repository.add(protector.hash(DIRECT_CODE), entitlement(1L, "ENT-DIRECT", 10L));
        repository.add(protector.hash(FRANCHISE_CODE), entitlement(2L, "ENT-FRANCHISE", 11L));
        CampaignRepository campaigns = mock(CampaignRepository.class);
        when(campaigns.findStores(10L)).thenReturn(Arrays.asList(
                new CampaignStoreSnapshot(1L, "QH001", StoreOwnershipType.DIRECT,
                        0L, "ACTIVE", 3L)));
        when(campaigns.findStores(11L)).thenReturn(Arrays.asList(
                new CampaignStoreSnapshot(6L, "QH006", StoreOwnershipType.FRANCHISE,
                        380L, "ACTIVE", 4L)));
        BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
        AtomicInteger sequence = new AtomicInteger();
        when(ids.next(BusinessIdType.REDEMPTION)).thenAnswer(
                ignored -> "RDM-" + sequence.incrementAndGet());
        RedemptionTransactionService transactions = new RedemptionTransactionService(
                repository, protector, new CampaignStorePolicy(campaigns), ids, clock);
        service = new PosRedemptionService(transactions, repository);
        directStore = new PosAuthenticatedStore(1L, "QH001", StoreOwnershipType.DIRECT,
                "pos-client-qh001");
        franchiseStore = new PosAuthenticatedStore(6L, "QH006", StoreOwnershipType.FRANCHISE,
                "pos-client-qh006");
    }

    @Test
    void sameRequestNumberAndDigestMustReturnTheFirstRedemptionResult() {
        PosRedemptionCommand command = command("REDEEM-QH006-0001", "ORDER-1", FRANCHISE_CODE);
        RedemptionResult first = service.redeem(franchiseStore, command);
        RedemptionResult replay = service.redeem(franchiseStore, command);
        assertEquals(first.redemptionNo(), replay.redemptionNo());
        assertEquals(1, repository.redemptions.size());
        assertEquals(1, repository.candidates.size());
    }

    @Test
    void sameRequestNumberWithDifferentDigestMustReturnRequestConflict() {
        service.redeem(franchiseStore,
                command("REDEEM-QH006-0002", "ORDER-ORIGINAL", FRANCHISE_CODE));
        QingheBusinessException conflict = assertThrows(QingheBusinessException.class,
                () -> service.redeem(franchiseStore,
                        command("REDEEM-QH006-0002", "ORDER-CHANGED", FRANCHISE_CODE)));
        assertEquals(QingheErrorCode.REQUEST_CONFLICT, conflict.errorCode());
        assertEquals(1, repository.redemptions.size());
    }

    @Test
    void concurrentRequestsForOneEntitlementMustHaveAtMostOneSuccess() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> one = pool.submit(() -> runAfter(start,
                    command("REDEEM-QH006-1001", "ORDER-A", FRANCHISE_CODE)));
            Future<Object> two = pool.submit(() -> runAfter(start,
                    command("REDEEM-QH006-1002", "ORDER-B", FRANCHISE_CODE)));
            start.countDown();
            Object first = one.get();
            Object second = two.get();
            assertNotEquals(first.getClass(), second.getClass());
            QingheBusinessException failure = first instanceof QingheBusinessException
                    ? (QingheBusinessException) first : (QingheBusinessException) second;
            assertEquals(QingheErrorCode.ALREADY_REDEEMED, failure.errorCode());
            assertEquals(1, repository.redemptions.size());
            assertEquals(1, repository.candidates.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aLostResponseMustBeRecoverableByTheOriginalRequestNumber() {
        RedemptionResult committed = service.redeem(franchiseStore,
                command("REDEEM-QH006-0004", "ORDER-4", FRANCHISE_CODE));
        RedemptionResult recovered = service.query(franchiseStore, "REDEEM-QH006-0004");
        assertEquals(PosRequestStatus.SUCCESS, recovered.status());
        assertEquals(committed.redemptionNo(), recovered.redemptionNo());
    }

    @Test
    void directStoreMustNotCreateSubsidyCandidateButFranchiseStoreMust() {
        service.redeem(directStore, new PosRedemptionCommand("REDEEM-QH001-0005", "ORDER-D",
                "QH001", "T01", "OP01", DIRECT_CODE,
                LocalDateTime.of(2026, 9, 9, 16, 0)));
        service.redeem(franchiseStore,
                command("REDEEM-QH006-0005", "ORDER-F", FRANCHISE_CODE));
        assertEquals(2, repository.redemptions.size());
        assertEquals(1, repository.candidates.size());
        SubsidyCandidate candidate = repository.candidates.values().iterator().next();
        assertEquals(6L, candidate.storeId());
        assertEquals(380L, candidate.subsidyFen());
        assertEquals(4L, candidate.snapshotVersion());
    }

    private Object runAfter(CountDownLatch start, PosRedemptionCommand command) throws Exception {
        start.await();
        try { return service.redeem(franchiseStore, command); }
        catch (QingheBusinessException failure) { return failure; }
    }

    private static PosRedemptionCommand command(String requestNo, String orderNo, String rightCode) {
        return new PosRedemptionCommand(requestNo, orderNo, "QH006", "T03", "OP18",
                rightCode, LocalDateTime.of(2026, 9, 9, 16, 0));
    }

    private static PosEntitlementSnapshot entitlement(long id, String no, long campaignId) {
        return new PosEntitlementSnapshot(id, no, campaignId, EntitlementStatus.AVAILABLE,
                LocalDateTime.of(2026, 9, 9, 15, 0), LocalDateTime.of(2026, 9, 16, 0, 0),
                0L, "免费饮品", BenefitType.FREE_PRODUCT, "DRINK-M", null);
    }

    private static final class InMemoryRedemptionRepository implements RedemptionRepository {
        private final Map<String, PosEntitlementSnapshot> entitlements = new ConcurrentHashMap<String, PosEntitlementSnapshot>();
        private final Map<String, PosRedemptionRequest> requests = new ConcurrentHashMap<String, PosRedemptionRequest>();
        private final Map<Long, Redemption> redemptions = new ConcurrentHashMap<Long, Redemption>();
        private final Map<Long, SubsidyCandidate> candidates = new ConcurrentHashMap<Long, SubsidyCandidate>();
        private final AtomicInteger requestIds = new AtomicInteger();
        private final AtomicInteger redemptionIds = new AtomicInteger();

        void add(String hash, PosEntitlementSnapshot entitlement) { entitlements.put(hash, entitlement); }
        @Override public void createRequestIfAbsent(String client, String requestNo, String digest,
                                                     long storeId, LocalDateTime now) {
            requests.putIfAbsent(key(client, requestNo), new PosRedemptionRequest(
                    requestIds.incrementAndGet(), client, requestNo, digest,
                    PosRequestStatus.PROCESSING, null, null, null, null, null, 0L));
        }
        @Override public Optional<PosRedemptionRequest> findRequest(String client, String requestNo) {
            return Optional.ofNullable(requests.get(key(client, requestNo)));
        }
        @Override public Optional<PosRedemptionRequest> findRequestForUpdate(String client, String requestNo) {
            return findRequest(client, requestNo);
        }
        @Override public Optional<PosEntitlementSnapshot> findEntitlementByRightCodeHash(String hash) {
            return Optional.ofNullable(entitlements.get(hash));
        }
        @Override public Optional<PosEntitlementSnapshot> findEntitlementByRightCodeHashForUpdate(String hash) {
            return findEntitlementByRightCodeHash(hash);
        }
        @Override public Optional<String> findSuccessfulRedemptionNoByEntitlementId(long id) {
            return redemptions.values().stream().filter(r -> r.entitlementId() == id)
                    .map(Redemption::redemptionNo).findFirst();
        }
        @Override public synchronized boolean markEntitlementUsed(long id, long version, LocalDateTime now) {
            Map.Entry<String, PosEntitlementSnapshot> entry = entitlements.entrySet().stream()
                    .filter(item -> item.getValue().id() == id).findFirst().orElse(null);
            if (entry == null || entry.getValue().status() != EntitlementStatus.AVAILABLE) return false;
            PosEntitlementSnapshot before = entry.getValue();
            entitlements.put(entry.getKey(), new PosEntitlementSnapshot(before.id(), before.entitlementNo(),
                    before.campaignId(), EntitlementStatus.USED, before.validFrom(), before.validUntil(),
                    before.version() + 1, before.title(), before.benefitType(), before.productCode(),
                    before.benefitValueFen()));
            return true;
        }
        @Override public Redemption insertRedemption(Redemption value, LocalDateTime now) {
            long id = redemptionIds.incrementAndGet();
            Redemption inserted = new Redemption(id, value.redemptionNo(), value.posClientId(),
                    value.posRequestNo(), value.requestDigest(), value.posOrderNo(), value.terminalNo(),
                    value.operatorNo(), value.entitlementId(), value.storeId(), value.status(),
                    value.occurredAt(), value.firstProcessedAt());
            redemptions.put(id, inserted);
            return inserted;
        }
        @Override public void insertSubsidyCandidate(SubsidyCandidate candidate) {
            candidates.put(candidate.redemptionId(), candidate);
        }
        @Override public void completeRequestSuccess(long id, long version, long redemptionId, LocalDateTime at) {
            replace(id, request -> new PosRedemptionRequest(request.id(), request.posClientId(),
                    request.posRequestNo(), request.requestDigest(), PosRequestStatus.SUCCESS,
                    redemptions.get(redemptionId).redemptionNo(), null, EntitlementStatus.USED,
                    null, at, version + 1));
        }
        @Override public void completeRequestFailure(long id, long version, String failureCode,
                                                      EntitlementStatus rightStatus, String original,
                                                      LocalDateTime at) {
            replace(id, request -> new PosRedemptionRequest(request.id(), request.posClientId(),
                    request.posRequestNo(), request.requestDigest(), PosRequestStatus.FAILED,
                    null, failureCode, rightStatus, original, at, version + 1));
        }
        private synchronized void replace(long id,
                java.util.function.Function<PosRedemptionRequest, PosRedemptionRequest> change) {
            Map.Entry<String, PosRedemptionRequest> entry = requests.entrySet().stream()
                    .filter(item -> item.getValue().id() == id).findFirst()
                    .orElseThrow(() -> new IllegalStateException("request missing"));
            requests.put(entry.getKey(), change.apply(entry.getValue()));
        }
        private static String key(String client, String requestNo) { return client + ":" + requestNo; }
    }
}
