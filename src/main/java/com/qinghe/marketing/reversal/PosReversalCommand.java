package com.qinghe.marketing.reversal;

import java.time.LocalDateTime;

public final class PosReversalCommand {
    private final String redemptionNo;
    private final String posRequestNo;
    private final String posOrderNo;
    private final String storeCode;
    private final String operatorNo;
    private final ReversalReason reasonCode;
    private final String reasonRemark;
    private final LocalDateTime occurredAt;

    public PosReversalCommand(String redemptionNo, String posRequestNo, String posOrderNo,
                              String storeCode, String operatorNo, ReversalReason reasonCode,
                              String reasonRemark, LocalDateTime occurredAt) {
        this.redemptionNo = redemptionNo; this.posRequestNo = posRequestNo;
        this.posOrderNo = posOrderNo; this.storeCode = storeCode; this.operatorNo = operatorNo;
        this.reasonCode = reasonCode; this.reasonRemark = reasonRemark; this.occurredAt = occurredAt;
    }
    public String redemptionNo() { return redemptionNo; }
    public String posRequestNo() { return posRequestNo; }
    public String posOrderNo() { return posOrderNo; }
    public String storeCode() { return storeCode; }
    public String operatorNo() { return operatorNo; }
    public ReversalReason reasonCode() { return reasonCode; }
    public String reasonRemark() { return reasonRemark; }
    public LocalDateTime occurredAt() { return occurredAt; }
}
