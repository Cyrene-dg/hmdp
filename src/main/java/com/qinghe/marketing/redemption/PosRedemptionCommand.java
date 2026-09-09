package com.qinghe.marketing.redemption;

import java.time.LocalDateTime;

public final class PosRedemptionCommand {
    private final String posRequestNo;
    private final String posOrderNo;
    private final String storeCode;
    private final String terminalNo;
    private final String operatorNo;
    private final String rightCode;
    private final LocalDateTime occurredAt;

    public PosRedemptionCommand(String posRequestNo, String posOrderNo, String storeCode,
                                String terminalNo, String operatorNo, String rightCode,
                                LocalDateTime occurredAt) {
        this.posRequestNo = posRequestNo;
        this.posOrderNo = posOrderNo;
        this.storeCode = storeCode;
        this.terminalNo = terminalNo;
        this.operatorNo = operatorNo;
        this.rightCode = rightCode;
        this.occurredAt = occurredAt;
    }
    public String posRequestNo() { return posRequestNo; }
    public String posOrderNo() { return posOrderNo; }
    public String storeCode() { return storeCode; }
    public String terminalNo() { return terminalNo; }
    public String operatorNo() { return operatorNo; }
    public String rightCode() { return rightCode; }
    public LocalDateTime occurredAt() { return occurredAt; }
}
