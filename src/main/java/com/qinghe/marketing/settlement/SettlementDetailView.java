package com.qinghe.marketing.settlement;

import java.time.LocalDateTime;

public final class SettlementDetailView {
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

    public SettlementDetailView(String campaignNo,String storeCode,String storeName,
            String redemptionNo,String posRequestNo,String posOrderNo,long subsidyFen,
            String reconciliationStatus,String confirmationStatus,LocalDateTime confirmedAt) {
        this.campaignNo=campaignNo; this.storeCode=storeCode; this.storeName=storeName;
        this.redemptionNo=redemptionNo; this.posRequestNo=posRequestNo;
        this.posOrderNo=posOrderNo; this.subsidyFen=subsidyFen;
        this.reconciliationStatus=reconciliationStatus;
        this.confirmationStatus=confirmationStatus; this.confirmedAt=confirmedAt;
    }
    public String getCampaignNo() { return campaignNo; }
    public String getStoreCode() { return storeCode; }
    public String getStoreName() { return storeName; }
    public String getRedemptionNo() { return redemptionNo; }
    public String getPosRequestNo() { return posRequestNo; }
    public String getPosOrderNo() { return posOrderNo; }
    public long getSubsidyFen() { return subsidyFen; }
    public String getReconciliationStatus() { return reconciliationStatus; }
    public String getConfirmationStatus() { return confirmationStatus; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
}
