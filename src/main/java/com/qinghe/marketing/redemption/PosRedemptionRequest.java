package com.qinghe.marketing.redemption;

import com.qinghe.marketing.entitlement.EntitlementStatus;
import java.time.LocalDateTime;

public final class PosRedemptionRequest {
    private final long id;
    private final String posClientId;
    private final String posRequestNo;
    private final String requestDigest;
    private final PosRequestStatus status;
    private final String redemptionNo;
    private final String failureCode;
    private final EntitlementStatus rightStatus;
    private final String originalRedemptionNo;
    private final LocalDateTime firstProcessedAt;
    private final long version;

    public PosRedemptionRequest(long id, String posClientId, String posRequestNo,
                                String requestDigest, PosRequestStatus status, String redemptionNo,
                                String failureCode, EntitlementStatus rightStatus,
                                String originalRedemptionNo, LocalDateTime firstProcessedAt,
                                long version) {
        this.id = id; this.posClientId = posClientId; this.posRequestNo = posRequestNo;
        this.requestDigest = requestDigest; this.status = status; this.redemptionNo = redemptionNo;
        this.failureCode = failureCode; this.rightStatus = rightStatus;
        this.originalRedemptionNo = originalRedemptionNo;
        this.firstProcessedAt = firstProcessedAt; this.version = version;
    }
    public long id() { return id; }
    public String posClientId() { return posClientId; }
    public String posRequestNo() { return posRequestNo; }
    public String requestDigest() { return requestDigest; }
    public PosRequestStatus status() { return status; }
    public String redemptionNo() { return redemptionNo; }
    public String failureCode() { return failureCode; }
    public EntitlementStatus rightStatus() { return rightStatus; }
    public String originalRedemptionNo() { return originalRedemptionNo; }
    public LocalDateTime firstProcessedAt() { return firstProcessedAt; }
    public long version() { return version; }
}
