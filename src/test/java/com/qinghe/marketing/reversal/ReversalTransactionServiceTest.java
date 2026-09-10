package com.qinghe.marketing.reversal;

import com.qinghe.marketing.entitlement.EntitlementStatus;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.redemption.RedemptionStatus;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReversalTransactionServiceTest {
    private FakeRepository repository;
    private ReversalTransactionService transactions;
    private PosReversalService service;
    private PosAuthenticatedStore store;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
        when(ids.next(BusinessIdType.REVERSAL)).thenReturn("REV-0001");
        BusinessClock clock = () -> Instant.parse("2026-09-09T08:00:00Z");
        transactions = new ReversalTransactionService(repository, ids, clock);
        service = new PosReversalService(transactions);
        store = new PosAuthenticatedStore(2L, "S002", StoreOwnershipType.FRANCHISE, "POS-2");
    }

    @Test
    void shouldKeepOriginalFactRestoreRightAndCancelUnconfirmedFinanceFacts() {
        ReversalResult result = service.reverse(store, command("REVREQ-0001", NOW));

        assertEquals("REV-0001", result.reversalNo());
        assertEquals(EntitlementStatus.AVAILABLE, result.rightStatus());
        assertEquals(RedemptionStatus.REVERSED, repository.redemption.status());
        assertEquals(EntitlementStatus.AVAILABLE, repository.entitlement.status());
        assertTrue(repository.candidateCancelled);
        assertTrue(repository.detailCancelled);

        ReversalResult replay = service.reverse(store, command("REVREQ-0001", NOW));
        assertEquals(result.reversalNo(), replay.reversalNo());
        assertTrue(replay.replay());
        assertEquals(1, repository.reversalInsertions);
    }

    @Test
    void shouldRestoreExpiredRightAsExpired() {
        repository.entitlement = entitlement(EntitlementStatus.USED, NOW.minusSeconds(1), 0);
        ReversalResult result = service.reverse(store, command("REVREQ-0002", NOW));
        assertEquals(EntitlementStatus.EXPIRED, result.rightStatus());
    }

    @Test
    void shouldPersistCrossDayAndSettlementLockedFailures() {
        QingheBusinessException crossDay = assertThrows(QingheBusinessException.class,
                () -> service.reverse(store, command("REVREQ-0003", NOW.plusDays(1))));
        assertEquals(QingheErrorCode.REVERSAL_NOT_ALLOWED, crossDay.errorCode());
        repository.locked = true;
        QingheBusinessException locked = assertThrows(QingheBusinessException.class,
                () -> service.reverse(store, command("REVREQ-0004", NOW)));
        assertEquals(QingheErrorCode.SETTLEMENT_LOCKED, locked.errorCode());
        assertEquals(409, locked.httpStatus());
        assertEquals(2, repository.failures);
    }

    @Test
    void shouldRejectChangedContentForSameRequestWithoutSecondMutation() {
        service.reverse(store, command("REVREQ-0005", NOW));
        PosReversalCommand changed = new PosReversalCommand("RDM-0001", "REVREQ-0005",
                "ORDER-DIFFERENT", "S002", "OP-1", ReversalReason.OTHER, null, NOW);
        QingheBusinessException conflict = assertThrows(QingheBusinessException.class,
                () -> service.reverse(store, changed));
        assertEquals(QingheErrorCode.REQUEST_CONFLICT, conflict.errorCode());
        assertEquals(1, repository.reversalInsertions);
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 9, 16, 0);
    private static PosReversalCommand command(String requestNo, LocalDateTime occurredAt) {
        return new PosReversalCommand("RDM-0001", requestNo, "ORDER-0001", "S002", "OP-1",
                ReversalReason.POS_ORDER_CANCELLED, "顾客取消", occurredAt);
    }
    private static ReversalEntitlement entitlement(EntitlementStatus status,
                                                    LocalDateTime validUntil, long version) {
        return new ReversalEntitlement(10L, status, validUntil, version);
    }

    private static final class FakeRepository implements ReversalRepository {
        private final Map<String, PosReversalRequest> requests = new HashMap<String, PosReversalRequest>();
        private ReversibleRedemption redemption = new ReversibleRedemption(1L, "RDM-0001",
                "ORDER-0001", 10L, 2L, RedemptionStatus.SUCCESS, NOW, 0L);
        private ReversalEntitlement entitlement = entitlement(EntitlementStatus.USED, NOW.plusDays(1), 0);
        private boolean locked;
        private boolean candidateCancelled;
        private boolean detailCancelled;
        private int reversalInsertions;
        private int failures;

        public void createRequestIfAbsent(String clientId, String requestNo, String digest,
                                          String redemptionNo, long storeId, LocalDateTime now) {
            requests.putIfAbsent(requestNo, new PosReversalRequest(requests.size() + 1L,
                    requestNo, digest, redemptionNo, ReversalRequestStatus.PROCESSING,
                    null, null, null, null, 0L));
        }
        public Optional<PosReversalRequest> findRequestForUpdate(String clientId, String requestNo) {
            return Optional.ofNullable(requests.get(requestNo));
        }
        public Optional<ReversibleRedemption> findRedemptionForUpdate(String redemptionNo) {
            return this.redemption.redemptionNo().equals(redemptionNo)
                    ? Optional.of(this.redemption) : Optional.empty();
        }
        public Optional<ReversalEntitlement> findEntitlementForUpdate(long entitlementId) {
            return Optional.of(entitlement);
        }
        public boolean isSettlementLocked(long redemptionId) { return locked; }
        public void markRedemptionReversed(long id, long version, LocalDateTime now) {
            redemption = new ReversibleRedemption(id, redemption.redemptionNo(), redemption.posOrderNo(),
                    redemption.entitlementId(), redemption.storeId(), RedemptionStatus.REVERSED,
                    redemption.occurredAt(), version + 1);
        }
        public void restoreEntitlement(long id, long version, EntitlementStatus target, LocalDateTime now) {
            entitlement = entitlement(target, entitlement.validUntil(), version + 1);
        }
        public void cancelSubsidyCandidate(long redemptionId, LocalDateTime now) {
            candidateCancelled = true;
        }
        public void cancelPendingSettlementDetail(long redemptionId, LocalDateTime now) {
            detailCancelled = true;
        }
        public RedemptionReversal insertReversal(RedemptionReversal value, LocalDateTime now) {
            reversalInsertions++;
            return new RedemptionReversal(1L, value.reversalNo(), value.posClientId(),
                    value.posRequestNo(), value.requestDigest(), value.redemptionId(), value.status(),
                    value.reasonCode(), value.operatorNo(), value.reasonRemark(), value.occurredAt(),
                    value.firstProcessedAt());
        }
        public void completeSuccess(long requestId, long version, long redemptionId, long reversalId,
                                    EntitlementStatus rightStatus, LocalDateTime now) {
            PosReversalRequest prior = byId(requestId);
            requests.put(prior.posRequestNo(), new PosReversalRequest(prior.id(), prior.posRequestNo(),
                    prior.requestDigest(), prior.targetRedemptionNo(), ReversalRequestStatus.SUCCESS,
                    "REV-0001", null, rightStatus, now, version + 1));
        }
        public void completeFailure(long requestId, long version, String failureCode,
                                    EntitlementStatus rightStatus, LocalDateTime now) {
            failures++;
            PosReversalRequest prior = byId(requestId);
            requests.put(prior.posRequestNo(), new PosReversalRequest(prior.id(), prior.posRequestNo(),
                    prior.requestDigest(), prior.targetRedemptionNo(), ReversalRequestStatus.FAILED,
                    null, failureCode, rightStatus, now, version + 1));
        }
        private PosReversalRequest byId(long id) {
            return requests.values().stream().filter(value -> value.id() == id).findFirst().get();
        }
    }
}
