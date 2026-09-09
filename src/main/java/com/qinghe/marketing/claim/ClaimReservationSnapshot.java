package com.qinghe.marketing.claim;

import java.time.LocalDateTime;

public final class ClaimReservationSnapshot {

    private final long campaignId;
    private final long memberId;
    private final String requestId;
    private final String reservationId;
    private final LocalDateTime reservedAt;

    public ClaimReservationSnapshot(long campaignId, long memberId, String requestId,
                                    String reservationId, LocalDateTime reservedAt) {
        this.campaignId = campaignId;
        this.memberId = memberId;
        this.requestId = requestId;
        this.reservationId = reservationId;
        this.reservedAt = reservedAt;
    }

    public long campaignId() { return campaignId; }
    public long memberId() { return memberId; }
    public String requestId() { return requestId; }
    public String reservationId() { return reservationId; }
    public LocalDateTime reservedAt() { return reservedAt; }
}
