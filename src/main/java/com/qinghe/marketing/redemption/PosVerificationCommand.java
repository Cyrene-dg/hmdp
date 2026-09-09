package com.qinghe.marketing.redemption;

import java.time.LocalDateTime;

public final class PosVerificationCommand {
    private final String posRequestNo;
    private final String storeCode;
    private final String terminalNo;
    private final String rightCode;
    private final LocalDateTime requestedAt;

    public PosVerificationCommand(String posRequestNo, String storeCode, String terminalNo,
                                  String rightCode, LocalDateTime requestedAt) {
        this.posRequestNo = posRequestNo;
        this.storeCode = storeCode;
        this.terminalNo = terminalNo;
        this.rightCode = rightCode;
        this.requestedAt = requestedAt;
    }
    public String posRequestNo() { return posRequestNo; }
    public String storeCode() { return storeCode; }
    public String terminalNo() { return terminalNo; }
    public String rightCode() { return rightCode; }
    public LocalDateTime requestedAt() { return requestedAt; }
}
