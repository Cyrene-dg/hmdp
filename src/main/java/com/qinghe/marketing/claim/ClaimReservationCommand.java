package com.qinghe.marketing.claim;

import java.time.LocalDateTime;

public final class ClaimReservationCommand {

    private final long campaignId;
    private final long memberId;
    private final String requestId;
    private final String requestDigest;
    private final String reservationId;
    private final String claimNo;
    private final String eventId;
    private final LocalDateTime now;
    private final LocalDateTime claimBeginAt;
    private final LocalDateTime claimEndAt;

    public ClaimReservationCommand(long campaignId, long memberId, String requestId,
                                   String requestDigest, String reservationId, String claimNo,
                                   String eventId, LocalDateTime now,
                                   LocalDateTime claimBeginAt, LocalDateTime claimEndAt) {
        this.campaignId = campaignId;
        this.memberId = memberId;
        this.requestId = requestId;
        this.requestDigest = requestDigest;
        this.reservationId = reservationId;
        this.claimNo = claimNo;
        this.eventId = eventId;
        this.now = now;
        this.claimBeginAt = claimBeginAt;
        this.claimEndAt = claimEndAt;
    }

    public long campaignId() { return campaignId; }
    public long memberId() { return memberId; }
    public String requestId() { return requestId; }
    public String requestDigest() { return requestDigest; }
    public String reservationId() { return reservationId; }
    public String claimNo() { return claimNo; }
    public String eventId() { return eventId; }
    public LocalDateTime now() { return now; }
    public LocalDateTime claimBeginAt() { return claimBeginAt; }
    public LocalDateTime claimEndAt() { return claimEndAt; }
}
