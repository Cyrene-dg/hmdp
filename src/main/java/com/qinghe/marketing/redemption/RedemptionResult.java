package com.qinghe.marketing.redemption;

import com.qinghe.marketing.entitlement.EntitlementStatus;
import java.time.LocalDateTime;

public final class RedemptionResult {
    private final String posRequestNo;
    private final PosRequestStatus status;
    private final String redemptionNo;
    private final LocalDateTime firstProcessedAt;
    private final EntitlementStatus rightStatus;
    private final String failureCode;
    private final String originalRedemptionNo;

    public RedemptionResult(String posRequestNo, PosRequestStatus status, String redemptionNo,
                            LocalDateTime firstProcessedAt, EntitlementStatus rightStatus,
                            String failureCode, String originalRedemptionNo) {
        this.posRequestNo = posRequestNo; this.status = status; this.redemptionNo = redemptionNo;
        this.firstProcessedAt = firstProcessedAt; this.rightStatus = rightStatus;
        this.failureCode = failureCode; this.originalRedemptionNo = originalRedemptionNo;
    }
    public static RedemptionResult from(PosRedemptionRequest request) {
        return new RedemptionResult(request.posRequestNo(), request.status(), request.redemptionNo(),
                request.firstProcessedAt(), request.rightStatus(), request.failureCode(),
                request.originalRedemptionNo());
    }
    public String posRequestNo() { return posRequestNo; }
    public PosRequestStatus status() { return status; }
    public String redemptionNo() { return redemptionNo; }
    public LocalDateTime firstProcessedAt() { return firstProcessedAt; }
    public EntitlementStatus rightStatus() { return rightStatus; }
    public String failureCode() { return failureCode; }
    public String originalRedemptionNo() { return originalRedemptionNo; }
}
