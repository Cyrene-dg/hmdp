package com.qinghe.marketing.reconciliation;

import java.time.LocalDateTime;

public final class ReconciliationAttemptView {
    private final String checksum;
    private final String result;
    private final LocalDateTime receivedAt;
    public ReconciliationAttemptView(String checksum,String result,LocalDateTime receivedAt) {
        this.checksum=checksum; this.result=result; this.receivedAt=receivedAt;
    }
    public String getChecksum() { return checksum; }
    public String getResult() { return result; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
}
