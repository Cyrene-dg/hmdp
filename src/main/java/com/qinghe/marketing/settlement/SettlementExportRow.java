package com.qinghe.marketing.settlement;

import java.time.LocalDate;

public final class SettlementExportRow {
    private final String settlementBatchNo;
    private final LocalDate businessDate;
    private final String campaignNo;
    private final String storeCode;
    private final String redemptionNo;
    private final String posRequestNo;
    private final long subsidyFen;
    private final String reconciliationStatus;
    private final String confirmationStatus;

    public SettlementExportRow(String settlementBatchNo, LocalDate businessDate,
                               String campaignNo, String storeCode, String redemptionNo,
                               String posRequestNo, long subsidyFen,
                               String reconciliationStatus, String confirmationStatus) {
        this.settlementBatchNo = settlementBatchNo; this.businessDate = businessDate;
        this.campaignNo = campaignNo; this.storeCode = storeCode;
        this.redemptionNo = redemptionNo; this.posRequestNo = posRequestNo;
        this.subsidyFen = subsidyFen; this.reconciliationStatus = reconciliationStatus;
        this.confirmationStatus = confirmationStatus;
    }
    public String settlementBatchNo() { return settlementBatchNo; }
    public LocalDate businessDate() { return businessDate; }
    public String campaignNo() { return campaignNo; }
    public String storeCode() { return storeCode; }
    public String redemptionNo() { return redemptionNo; }
    public String posRequestNo() { return posRequestNo; }
    public long subsidyFen() { return subsidyFen; }
    public String reconciliationStatus() { return reconciliationStatus; }
    public String confirmationStatus() { return confirmationStatus; }
}
