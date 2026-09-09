package com.qinghe.marketing.claim;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

public interface ClaimRequestRepository {

    ClaimRequest insert(ClaimRequest claimRequest);

    Optional<ClaimRequest> findByMemberAndRequestId(long memberId, String requestId);

    Optional<ClaimRequest> findByReservationId(String reservationId);

    Optional<ClaimRequest> findByClaimNoAndMemberId(String claimNo, long memberId);

    Optional<ClaimRequest> findByClaimNo(String claimNo);

    Optional<ClaimRequest> findByClaimNoForUpdate(String claimNo);

    Optional<ClaimRequest> findByMemberCampaignAndCycle(long memberId, long campaignId,
                                                         String claimCycle);

    default Optional<String> findEntitlementNo(long claimId) {
        return Optional.empty();
    }

    boolean markSuccess(long claimId, long expectedVersion, LocalDateTime now);

    boolean beginCompensation(long claimId, long expectedVersion, String failureCode,
                              LocalDateTime now);

    boolean markFailed(long claimId, long expectedVersion, String failureCode,
                       LocalDateTime now);

    List<ClaimRequest> findCompensatingBefore(LocalDateTime cutoff, int limit);
}
