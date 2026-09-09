package com.qinghe.marketing.claim;

import java.time.LocalDateTime;

public final class ClaimRequest {

    private final long id;
    private final String claimNo;
    private final String requestId;
    private final String requestDigest;
    private final long campaignId;
    private final long memberId;
    private final String claimCycle;
    private final String reservationId;
    private final ClaimStatus status;
    private final String failureCode;
    private final long version;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public ClaimRequest(long id, String claimNo, String requestId, String requestDigest,
                        long campaignId, long memberId, String claimCycle, String reservationId,
                        ClaimStatus status, String failureCode, long version,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.claimNo = claimNo;
        this.requestId = requestId;
        this.requestDigest = requestDigest;
        this.campaignId = campaignId;
        this.memberId = memberId;
        this.claimCycle = claimCycle;
        this.reservationId = reservationId;
        this.status = status;
        this.failureCode = failureCode;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long id() { return id; }
    public String claimNo() { return claimNo; }
    public String requestId() { return requestId; }
    public String requestDigest() { return requestDigest; }
    public long campaignId() { return campaignId; }
    public long memberId() { return memberId; }
    public String claimCycle() { return claimCycle; }
    public String reservationId() { return reservationId; }
    public ClaimStatus status() { return status; }
    public String failureCode() { return failureCode; }
    public long version() { return version; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}
