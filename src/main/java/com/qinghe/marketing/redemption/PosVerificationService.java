package com.qinghe.marketing.redemption;

import com.qinghe.marketing.entitlement.EntitlementStatus;
import com.qinghe.marketing.entitlement.RightCodeProtector;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PosVerificationService {

    private final RedemptionRepository repository;
    private final RightCodeProtector rightCodeProtector;
    private final CampaignStorePolicy storePolicy;
    private final BusinessClock clock;

    public PosVerificationService(RedemptionRepository repository,
                                  RightCodeProtector rightCodeProtector,
                                  CampaignStorePolicy storePolicy, BusinessClock clock) {
        this.repository = repository;
        this.rightCodeProtector = rightCodeProtector;
        this.storePolicy = storePolicy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PosVerificationResult verify(PosAuthenticatedStore store,
                                        PosVerificationCommand command) {
        PosCommandValidator.validate(command);
        requireStoreMatches(store, command.storeCode());
        PosEntitlementSnapshot entitlement = repository.findEntitlementByRightCodeHash(
                        rightCodeProtector.hash(command.rightCode()))
                .orElseThrow(() -> failure(QingheErrorCode.RIGHT_NOT_FOUND,
                        "right does not exist"));
        requireAvailable(entitlement, clock.dateTime());
        storePolicy.requireApplicable(entitlement.campaignId(), store);
        return new PosVerificationResult(entitlement);
    }

    static void requireStoreMatches(PosAuthenticatedStore store, String requestStoreCode) {
        if (store == null || !store.storeCode().equals(requestStoreCode)) {
            throw failure(QingheErrorCode.STORE_NOT_APPLICABLE,
                    "POS credential cannot operate for another store");
        }
    }

    static void requireAvailable(PosEntitlementSnapshot entitlement, LocalDateTime now) {
        if (entitlement.status() == EntitlementStatus.EXPIRED
                || !entitlement.validUntil().isAfter(now)) {
            throw failure(QingheErrorCode.RIGHT_EXPIRED, "right is expired");
        }
        if (entitlement.status() != EntitlementStatus.AVAILABLE
                || entitlement.validFrom().isAfter(now)) {
            throw failure(QingheErrorCode.RIGHT_NOT_AVAILABLE,
                    "right is not currently available");
        }
    }

    static QingheBusinessException failure(QingheErrorCode code, String message) {
        return new QingheBusinessException(code, message);
    }
}
