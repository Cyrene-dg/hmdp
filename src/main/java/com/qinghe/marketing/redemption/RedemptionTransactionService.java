package com.qinghe.marketing.redemption;

import com.qinghe.marketing.campaign.CampaignStoreSnapshot;
import com.qinghe.marketing.entitlement.EntitlementStatus;
import com.qinghe.marketing.entitlement.RightCodeProtector;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import com.qinghe.marketing.store.StoreOwnershipType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RedemptionTransactionService {

    private final RedemptionRepository repository;
    private final RightCodeProtector rightCodeProtector;
    private final CampaignStorePolicy storePolicy;
    private final BusinessIdGenerator idGenerator;
    private final BusinessClock clock;

    public RedemptionTransactionService(RedemptionRepository repository,
                                        RightCodeProtector rightCodeProtector,
                                        CampaignStorePolicy storePolicy,
                                        BusinessIdGenerator idGenerator,
                                        BusinessClock clock) {
        this.repository = repository;
        this.rightCodeProtector = rightCodeProtector;
        this.storePolicy = storePolicy;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public RedemptionResult redeem(PosAuthenticatedStore store, PosRedemptionCommand command) {
        PosCommandValidator.validate(command);
        PosVerificationService.requireStoreMatches(store, command.storeCode());
        String digest = PosRequestDigest.redemption(command);
        LocalDateTime now = clock.dateTime();
        repository.createRequestIfAbsent(store.clientId(), command.posRequestNo(), digest,
                store.storeId(), now);
        PosRedemptionRequest request = repository.findRequestForUpdate(
                        store.clientId(), command.posRequestNo())
                .orElseThrow(() -> new IllegalStateException("POS request cannot be reloaded"));
        if (!request.requestDigest().equals(digest)) {
            throw new QingheBusinessException(QingheErrorCode.REQUEST_CONFLICT,
                    "POS request number was reused with different business content",
                    request.redemptionNo());
        }
        if (request.status() != PosRequestStatus.PROCESSING) {
            return RedemptionResult.from(request);
        }

        PosEntitlementSnapshot entitlement = repository.findEntitlementByRightCodeHashForUpdate(
                        rightCodeProtector.hash(command.rightCode()))
                .orElse(null);
        if (entitlement == null) {
            return fail(request, QingheErrorCode.RIGHT_NOT_FOUND, null, null, now);
        }

        CampaignStoreSnapshot campaignStore;
        try {
            campaignStore = storePolicy.requireApplicable(entitlement.campaignId(), store);
        } catch (QingheBusinessException notApplicable) {
            return fail(request, QingheErrorCode.STORE_NOT_APPLICABLE,
                    entitlement.status(), null, now);
        }
        if (entitlement.status() == EntitlementStatus.EXPIRED
                || !entitlement.validUntil().isAfter(now)) {
            return fail(request, QingheErrorCode.RIGHT_EXPIRED,
                    EntitlementStatus.EXPIRED, null, now);
        }
        if (entitlement.status() != EntitlementStatus.AVAILABLE
                || entitlement.validFrom().isAfter(now)) {
            String original = entitlement.status() == EntitlementStatus.USED
                    ? repository.findSuccessfulRedemptionNoByEntitlementId(entitlement.id()).orElse(null)
                    : null;
            QingheErrorCode code = entitlement.status() == EntitlementStatus.USED
                    ? QingheErrorCode.ALREADY_REDEEMED : QingheErrorCode.RIGHT_NOT_AVAILABLE;
            return fail(request, code, entitlement.status(), original, now);
        }

        if (!repository.markEntitlementUsed(entitlement.id(), entitlement.version(), now)) {
            String original = repository.findSuccessfulRedemptionNoByEntitlementId(
                    entitlement.id()).orElse(null);
            return fail(request, QingheErrorCode.ALREADY_REDEEMED,
                    EntitlementStatus.USED, original, now);
        }
        Redemption redemption = repository.insertRedemption(new Redemption(0,
                idGenerator.next(BusinessIdType.REDEMPTION), store.clientId(),
                command.posRequestNo(), digest, command.posOrderNo(), command.terminalNo(),
                command.operatorNo(), entitlement.id(), store.storeId(), RedemptionStatus.SUCCESS,
                command.occurredAt(), now), now);

        if (campaignStore.ownershipType() == StoreOwnershipType.FRANCHISE) {
            if (campaignStore.subsidyFen() < 0) {
                throw new IllegalStateException("published franchise subsidy cannot be negative");
            }
            repository.insertSubsidyCandidate(new SubsidyCandidate(redemption.id(),
                    entitlement.campaignId(), store.storeId(), campaignStore.subsidyFen(),
                    SubsidyCandidateStatus.UNRECONCILED, campaignStore.ruleVersion(), now));
        } else if (campaignStore.subsidyFen() != 0) {
            throw new IllegalStateException("published direct-store subsidy must be zero");
        }
        repository.completeRequestSuccess(request.id(), request.version(), redemption.id(), now);
        return new RedemptionResult(command.posRequestNo(), PosRequestStatus.SUCCESS,
                redemption.redemptionNo(), now, EntitlementStatus.USED, null, null);
    }

    private RedemptionResult fail(PosRedemptionRequest request, QingheErrorCode code,
                                  EntitlementStatus rightStatus, String originalRedemptionNo,
                                  LocalDateTime now) {
        repository.completeRequestFailure(request.id(), request.version(), code.name(), rightStatus,
                originalRedemptionNo, now);
        return new RedemptionResult(request.posRequestNo(), PosRequestStatus.FAILED, null,
                now, rightStatus, code.name(), originalRedemptionNo);
    }
}
