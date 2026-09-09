package com.qinghe.marketing.reversal;

import java.time.LocalDateTime;

public final class RedemptionReversal {
    private final long id;
    private final String reversalNo;
    private final String posClientId;
    private final String posRequestNo;
    private final String requestDigest;
    private final long redemptionId;
    private final ReversalStatus status;
    private final ReversalReason reasonCode;
    private final String operatorNo;
    private final String reasonRemark;
    private final LocalDateTime occurredAt;
    private final LocalDateTime firstProcessedAt;

    public RedemptionReversal(long id, String reversalNo, String posClientId,
                              String posRequestNo, String requestDigest, long redemptionId,
                              ReversalStatus status, ReversalReason reasonCode,
                              String operatorNo, String reasonRemark, LocalDateTime occurredAt,
                              LocalDateTime firstProcessedAt) {
        this.id = id; this.reversalNo = reversalNo; this.posClientId = posClientId;
        this.posRequestNo = posRequestNo; this.requestDigest = requestDigest;
        this.redemptionId = redemptionId; this.status = status; this.reasonCode = reasonCode;
        this.operatorNo = operatorNo; this.reasonRemark = reasonRemark;
        this.occurredAt = occurredAt; this.firstProcessedAt = firstProcessedAt;
    }
    public long id() { return id; }
    public String reversalNo() { return reversalNo; }
    public String posClientId() { return posClientId; }
    public String posRequestNo() { return posRequestNo; }
    public String requestDigest() { return requestDigest; }
    public long redemptionId() { return redemptionId; }
    public ReversalStatus status() { return status; }
    public ReversalReason reasonCode() { return reasonCode; }
    public String operatorNo() { return operatorNo; }
    public String reasonRemark() { return reasonRemark; }
    public LocalDateTime occurredAt() { return occurredAt; }
    public LocalDateTime firstProcessedAt() { return firstProcessedAt; }
}
