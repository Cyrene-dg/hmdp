package com.qinghe.marketing.claim;

import java.time.LocalDateTime;
import java.util.List;

public interface ClaimReservationStore {

    ClaimReservationResult reserve(ClaimReservationCommand command);

    void markPersisted(long campaignId, String reservationId);

    void compensate(long campaignId, long memberId, String requestId,
                    String reservationId, String reason);

    List<ClaimReservationSnapshot> findPendingBefore(long campaignId,
                                                      LocalDateTime cutoff, int limit);
}
