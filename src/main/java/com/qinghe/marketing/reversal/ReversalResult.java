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
    private final boolean replay;

    public ReversalResult(String posRequestNo, String reversalNo, String redemptionNo,
                          ReversalRequestStatus status, EntitlementStatus rightStatus,
                          String failureCode, LocalDateTime firstProcessedAt) {
        this(posRequestNo, reversalNo, redemptionNo, status, rightStatus, failureCode,
                firstProcessedAt, false);
    }
    private ReversalResult(String posRequestNo, String reversalNo, String redemptionNo,
                           ReversalRequestStatus status, EntitlementStatus rightStatus,
                           String failureCode, LocalDateTime firstProcessedAt, boolean replay) {
        this.posRequestNo = posRequestNo; this.reversalNo = reversalNo;
        this.redemptionNo = redemptionNo; this.status = status; this.rightStatus = rightStatus;
        this.failureCode = failureCode; this.firstProcessedAt = firstProcessedAt;
        this.replay = replay;
    }
    public static ReversalResult from(PosReversalRequest request) {
        return new ReversalResult(request.posRequestNo(), request.reversalNo(),
                request.targetRedemptionNo(), request.status(), request.rightStatus(),
                request.failureCode(), request.firstProcessedAt());
    }
    public static ReversalResult replay(PosReversalRequest request) {
        return new ReversalResult(request.posRequestNo(), request.reversalNo(),
                request.targetRedemptionNo(), request.status(), request.rightStatus(),
                request.failureCode(), request.firstProcessedAt(), true);
    }
    public String posRequestNo() { return posRequestNo; }
    public String reversalNo() { return reversalNo; }
    public String redemptionNo() { return redemptionNo; }
    public ReversalRequestStatus status() { return status; }
    public EntitlementStatus rightStatus() { return rightStatus; }
    public String failureCode() { return failureCode; }
    public LocalDateTime firstProcessedAt() { return firstProcessedAt; }
    public boolean replay() { return replay; }
}
