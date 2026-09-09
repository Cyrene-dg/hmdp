package com.qinghe.marketing.reconciliation;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class ReconciliationCsvRow {
    private final int lineNo;
    private final String batchNo;
    private final LocalDate businessDate;
    private final String storeCode;
    private final String terminalNo;
    private final String posOrderNo;
    private final String posRequestNo;
    private final String redemptionNo;
    private final String rightCode;
    private final String operationType;
    private final String operationStatus;
    private final OffsetDateTime occurredAt;
    private final String rawDigest;

    public ReconciliationCsvRow(int lineNo, String batchNo, LocalDate businessDate,
                                String storeCode, String terminalNo, String posOrderNo,
                                String posRequestNo, String redemptionNo, String rightCode,
                                String operationType, String operationStatus,
                                OffsetDateTime occurredAt, String rawDigest) {
        this.lineNo = lineNo; this.batchNo = batchNo; this.businessDate = businessDate;
        this.storeCode = storeCode; this.terminalNo = terminalNo; this.posOrderNo = posOrderNo;
        this.posRequestNo = posRequestNo; this.redemptionNo = redemptionNo;
        this.rightCode = rightCode; this.operationType = operationType;
        this.operationStatus = operationStatus; this.occurredAt = occurredAt;
        this.rawDigest = rawDigest;
    }
    public int lineNo() { return lineNo; }
    public String batchNo() { return batchNo; }
    public LocalDate businessDate() { return businessDate; }
    public String storeCode() { return storeCode; }
    public String terminalNo() { return terminalNo; }
    public String posOrderNo() { return posOrderNo; }
    public String posRequestNo() { return posRequestNo; }
    public String redemptionNo() { return redemptionNo; }
    public String rightCode() { return rightCode; }
    public String operationType() { return operationType; }
    public String operationStatus() { return operationStatus; }
    public OffsetDateTime occurredAt() { return occurredAt; }
    public String rawDigest() { return rawDigest; }
}
