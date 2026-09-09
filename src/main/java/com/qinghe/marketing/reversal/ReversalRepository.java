package com.qinghe.marketing.reversal;

import com.qinghe.marketing.entitlement.EntitlementStatus;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ReversalRepository {
    void createRequestIfAbsent(String clientId, String requestNo, String digest,
                               String redemptionNo, long storeId, LocalDateTime now);
    Optional<PosReversalRequest> findRequestForUpdate(String clientId, String requestNo);
    Optional<ReversibleRedemption> findRedemptionForUpdate(String redemptionNo);
    Optional<ReversalEntitlement> findEntitlementForUpdate(long entitlementId);
    boolean isSettlementLocked(long redemptionId);
    void markRedemptionReversed(long redemptionId, long version, LocalDateTime now);
    void restoreEntitlement(long entitlementId, long version, EntitlementStatus target,
                            LocalDateTime now);
    void cancelSubsidyCandidate(long redemptionId, LocalDateTime now);
    void cancelPendingSettlementDetail(long redemptionId, LocalDateTime now);
    RedemptionReversal insertReversal(RedemptionReversal reversal, LocalDateTime now);
    void completeSuccess(long requestId, long version, long redemptionId, long reversalId,
                         EntitlementStatus rightStatus, LocalDateTime now);
    void completeFailure(long requestId, long version, String failureCode,
                         EntitlementStatus rightStatus, LocalDateTime now);
}
