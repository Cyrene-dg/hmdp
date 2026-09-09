package com.qinghe.marketing.claim;

import java.util.Optional;

public interface ClaimRequestRepository {

    ClaimRequest insert(ClaimRequest claimRequest);

    Optional<ClaimRequest> findByMemberAndRequestId(long memberId, String requestId);

    Optional<ClaimRequest> findByReservationId(String reservationId);

    Optional<ClaimRequest> findByClaimNoAndMemberId(String claimNo, long memberId);

    Optional<ClaimRequest> findByMemberCampaignAndCycle(long memberId, long campaignId,
                                                         String claimCycle);
}
