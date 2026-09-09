package com.qinghe.marketing.redemption;

import com.qinghe.marketing.entitlement.EntitlementStatus;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RedemptionRepository {

    void createRequestIfAbsent(String posClientId, String posRequestNo, String requestDigest,
                               long storeId, LocalDateTime now);

    Optional<PosRedemptionRequest> findRequest(String posClientId, String posRequestNo);

    Optional<PosRedemptionRequest> findRequestForUpdate(String posClientId, String posRequestNo);

    Optional<PosEntitlementSnapshot> findEntitlementByRightCodeHash(String rightCodeHash);

    Optional<PosEntitlementSnapshot> findEntitlementByRightCodeHashForUpdate(String rightCodeHash);

    Optional<String> findSuccessfulRedemptionNoByEntitlementId(long entitlementId);

    boolean markEntitlementUsed(long entitlementId, long expectedVersion, LocalDateTime now);

    Redemption insertRedemption(Redemption redemption, LocalDateTime now);

    void insertSubsidyCandidate(SubsidyCandidate candidate);

    void completeRequestSuccess(long requestId, long expectedVersion, long redemptionId,
                                LocalDateTime completedAt);

    void completeRequestFailure(long requestId, long expectedVersion, String failureCode,
                                EntitlementStatus rightStatus, String originalRedemptionNo,
                                LocalDateTime completedAt);
}
