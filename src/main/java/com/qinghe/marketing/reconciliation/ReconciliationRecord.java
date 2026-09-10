package com.qinghe.marketing.reconciliation;

import java.time.LocalDateTime;

public final class ReconciliationRecord {
    private final long id;
    private final long batchId;
    private final int lineNo;
    private final String storeCode;
    private final String terminalNo;
    private final String posOrderNo;
    private final String posRequestNo;
    private final String redemptionNo;
    private final String rightCodeHash;
    private final String operationType;
    private final String operationStatus;
    private final LocalDateTime occurredAt;

    public ReconciliationRecord(long id, long batchId, int lineNo, String storeCode,
                                String terminalNo, String posOrderNo, String posRequestNo,
                                String redemptionNo, String rightCodeHash, String operationType,
                                String operationStatus, LocalDateTime occurredAt) {
        this.id = id; this.batchId = batchId; this.lineNo = lineNo;
        this.storeCode = storeCode; this.terminalNo = terminalNo;
        this.posOrderNo = posOrderNo; this.posRequestNo = posRequestNo;
        this.redemptionNo = redemptionNo; this.rightCodeHash = rightCodeHash;
        this.operationType = operationType; this.operationStatus = operationStatus;
        this.occurredAt = occurredAt;
    }

    public long id() { return id; }
    public long batchId() { return batchId; }
    public int lineNo() { return lineNo; }
    public String storeCode() { return storeCode; }
    public String terminalNo() { return terminalNo; }
    public String posOrderNo() { return posOrderNo; }
    public String posRequestNo() { return posRequestNo; }
    public String redemptionNo() { return redemptionNo; }
    public String rightCodeHash() { return rightCodeHash; }
    public String operationType() { return operationType; }
    public String operationStatus() { return operationStatus; }
    public LocalDateTime occurredAt() { return occurredAt; }
}
