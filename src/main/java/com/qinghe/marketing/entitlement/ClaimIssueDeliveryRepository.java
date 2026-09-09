package com.qinghe.marketing.entitlement;

import java.time.LocalDateTime;

public interface ClaimIssueDeliveryRepository {

    void record(String eventId, long claimId, String outcome, String failureCode,
                LocalDateTime processedAt);

    boolean sourceEventMatches(String eventId, long claimId);

    boolean exists(String eventId);
}
