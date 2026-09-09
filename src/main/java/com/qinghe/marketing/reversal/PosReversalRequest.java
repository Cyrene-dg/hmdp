package com.qinghe.marketing.reversal;

import com.qinghe.marketing.entitlement.EntitlementStatus;
import java.time.LocalDateTime;

public final class PosReversalRequest {
    private final long id;
    private final String posRequestNo;
    private final String requestDigest;
    private final String targetRedemptionNo;
    private final ReversalRequestStatus status;
    private final String reversalNo;
    private final String failureCode;
    private final EntitlementStatus rightStatus;
    private final LocalDateTime firstProcessedAt;
    private final long version;

    public PosReversalRequest(long id, String posRequestNo, String requestDigest,
                              String targetRedemptionNo, ReversalRequestStatus status,
                              String reversalNo, String failureCode,
                              EntitlementStatus rightStatus, LocalDateTime firstProcessedAt,
                              long version) {
        this.id = id; this.posRequestNo = posRequestNo; this.requestDigest = requestDigest;
        this.targetRedemptionNo = targetRedemptionNo; this.status = status;
        this.reversalNo = reversalNo; this.failureCode = failureCode;
        this.rightStatus = rightStatus; this.firstProcessedAt = firstProcessedAt;
        this.version = version;
    }
    public long id() { return id; }
    public String posRequestNo() { return posRequestNo; }
    public String requestDigest() { return requestDigest; }
    public String targetRedemptionNo() { return targetRedemptionNo; }
    public ReversalRequestStatus status() { return status; }
    public String reversalNo() { return reversalNo; }
    public String failureCode() { return failureCode; }
    public EntitlementStatus rightStatus() { return rightStatus; }
    public LocalDateTime firstProcessedAt() { return firstProcessedAt; }
    public long version() { return version; }
}
