package com.qinghe.marketing.reversal;

import com.qinghe.marketing.entitlement.EntitlementStatus;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.redemption.RedemptionStatus;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class ReversalTransactionService {
    private final ReversalRepository repository;
    private final BusinessIdGenerator ids;
    private final BusinessClock clock;

    public ReversalTransactionService(ReversalRepository repository, BusinessIdGenerator ids,
                                      BusinessClock clock) {
        this.repository = repository; this.ids = ids; this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReversalResult reverse(PosAuthenticatedStore store, PosReversalCommand command) {
        ReversalCommandValidator.validate(command);
        requireStoreCode(store, command.storeCode());
        String digest = ReversalRequestDigest.calculate(command);
        LocalDateTime now = clock.dateTime();
        repository.createRequestIfAbsent(store.clientId(), command.posRequestNo(), digest,
                command.redemptionNo(), store.storeId(), now);
        PosReversalRequest request = repository.findRequestForUpdate(
                        store.clientId(), command.posRequestNo())
                .orElseThrow(() -> new IllegalStateException("reversal request cannot be reloaded"));
        if (!request.requestDigest().equals(digest)) {
            throw new QingheBusinessException(QingheErrorCode.REQUEST_CONFLICT,
                    "reversal request number was reused with different business content",
                    request.targetRedemptionNo());
        }
        if (request.status() != ReversalRequestStatus.PROCESSING) {
            return ReversalResult.from(request);
        }

        ReversibleRedemption redemption = repository.findRedemptionForUpdate(command.redemptionNo())
                .orElse(null);
        if (redemption == null) return fail(request, QingheErrorCode.REDEMPTION_NOT_FOUND, null, now);
        if (redemption.storeId() != store.storeId()
                || !redemption.posOrderNo().equals(command.posOrderNo())
                || redemption.status() != RedemptionStatus.SUCCESS) {
            return fail(request, QingheErrorCode.REVERSAL_NOT_ALLOWED, null, now);
        }
        LocalDate businessDate = clock.businessDate();
        if (!redemption.occurredAt().toLocalDate().equals(businessDate)
                || !command.occurredAt().toLocalDate().equals(businessDate)) {
            return fail(request, QingheErrorCode.REVERSAL_NOT_ALLOWED, null, now);
        }
        if (repository.isSettlementLocked(redemption.id())) {
            return fail(request, QingheErrorCode.SETTLEMENT_LOCKED, null, now);
        }
        ReversalEntitlement entitlement = repository.findEntitlementForUpdate(redemption.entitlementId())
                .orElseThrow(() -> new IllegalStateException("redemption entitlement is missing"));
        if (entitlement.status() != EntitlementStatus.USED) {
            return fail(request, QingheErrorCode.REVERSAL_NOT_ALLOWED,
                    entitlement.status(), now);
        }
        EntitlementStatus target = entitlement.validUntil().isAfter(now)
                ? EntitlementStatus.AVAILABLE : EntitlementStatus.EXPIRED;
        repository.markRedemptionReversed(redemption.id(), redemption.version(), now);
        repository.restoreEntitlement(entitlement.id(), entitlement.version(), target, now);
        repository.cancelPendingSettlementDetail(redemption.id(), now);
        repository.cancelSubsidyCandidate(redemption.id(), now);
        RedemptionReversal reversal = repository.insertReversal(new RedemptionReversal(0,
                ids.next(BusinessIdType.REVERSAL), store.clientId(), command.posRequestNo(),
                digest, redemption.id(), ReversalStatus.SUCCESS, command.reasonCode(),
                command.operatorNo(), command.reasonRemark(), command.occurredAt(), now), now);
        repository.completeSuccess(request.id(), request.version(), redemption.id(), reversal.id(),
                target, now);
        return new ReversalResult(command.posRequestNo(), reversal.reversalNo(),
                redemption.redemptionNo(), ReversalRequestStatus.SUCCESS, target, null, now);
    }

    private ReversalResult fail(PosReversalRequest request, QingheErrorCode code,
                                EntitlementStatus rightStatus, LocalDateTime now) {
        repository.completeFailure(request.id(), request.version(), code.name(), rightStatus, now);
        return new ReversalResult(request.posRequestNo(), null, request.targetRedemptionNo(),
                ReversalRequestStatus.FAILED, rightStatus, code.name(), now);
    }

    private static void requireStoreCode(PosAuthenticatedStore store, String storeCode) {
        if (store == null || !store.storeCode().equals(storeCode)) {
            throw new QingheBusinessException(QingheErrorCode.REVERSAL_NOT_ALLOWED,
                    "POS credential cannot reverse for another store");
        }
    }
}
