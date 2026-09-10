package com.qinghe.marketing.settlement;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SettlementExportRow {
    private final String settlementBatchNo;
    private final LocalDate businessDate;
    private final String campaignNo;
    private final String storeCode;
    private final String storeName;
    private final String redemptionNo;
    private final String posRequestNo;
    private final String posOrderNo;
    private final long subsidyFen;
    private final String reconciliationStatus;
    private final String confirmationStatus;
    private final LocalDateTime confirmedAt;

    public SettlementExportRow(String settlementBatchNo, LocalDate businessDate,
                               String campaignNo, String storeCode, String storeName,
                               String redemptionNo, String posRequestNo, String posOrderNo,
                               long subsidyFen, String reconciliationStatus,
                               String confirmationStatus, LocalDateTime confirmedAt) {
        this.settlementBatchNo = settlementBatchNo; this.businessDate = businessDate;
        this.campaignNo = campaignNo; this.storeCode = storeCode; this.storeName = storeName;
        this.redemptionNo = redemptionNo; this.posRequestNo = posRequestNo;
        this.posOrderNo = posOrderNo;
        this.subsidyFen = subsidyFen; this.reconciliationStatus = reconciliationStatus;
        this.confirmationStatus = confirmationStatus; this.confirmedAt = confirmedAt;
    }
    public String settlementBatchNo() { return settlementBatchNo; }
    public LocalDate businessDate() { return businessDate; }
    public String campaignNo() { return campaignNo; }
    public String storeCode() { return storeCode; }
    public String storeName() { return storeName; }
    public String redemptionNo() { return redemptionNo; }
    public String posRequestNo() { return posRequestNo; }
    public String posOrderNo() { return posOrderNo; }
    public long subsidyFen() { return subsidyFen; }
    public String reconciliationStatus() { return reconciliationStatus; }
    public String confirmationStatus() { return confirmationStatus; }
    public LocalDateTime confirmedAt() { return confirmedAt; }
}
