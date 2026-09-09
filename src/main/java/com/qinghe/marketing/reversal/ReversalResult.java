package com.qinghe.marketing.reversal;

import com.qinghe.marketing.entitlement.EntitlementStatus;
import java.time.LocalDateTime;

public final class ReversalResult {
    private final String posRequestNo;
    private final String reversalNo;
    private final String redemptionNo;
    private final ReversalRequestStatus status;
    private final EntitlementStatus rightStatus;
    private final String failureCode;
    private final LocalDateTime firstProcessedAt;

    public ReversalResult(String posRequestNo, String reversalNo, String redemptionNo,
                          ReversalRequestStatus status, EntitlementStatus rightStatus,
                          String failureCode, LocalDateTime firstProcessedAt) {
        this.posRequestNo = posRequestNo; this.reversalNo = reversalNo;
        this.redemptionNo = redemptionNo; this.status = status; this.rightStatus = rightStatus;
        this.failureCode = failureCode; this.firstProcessedAt = firstProcessedAt;
    }
    public static ReversalResult from(PosReversalRequest request) {
        return new ReversalResult(request.posRequestNo(), request.reversalNo(),
                request.targetRedemptionNo(), request.status(), request.rightStatus(),
                request.failureCode(), request.firstProcessedAt());
    }
    public String posRequestNo() { return posRequestNo; }
    public String reversalNo() { return reversalNo; }
    public String redemptionNo() { return redemptionNo; }
    public ReversalRequestStatus status() { return status; }
    public EntitlementStatus rightStatus() { return rightStatus; }
    public String failureCode() { return failureCode; }
    public LocalDateTime firstProcessedAt() { return firstProcessedAt; }
}
