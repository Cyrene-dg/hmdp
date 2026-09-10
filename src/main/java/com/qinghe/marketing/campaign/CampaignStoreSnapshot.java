package com.qinghe.marketing.campaign;

import com.qinghe.marketing.store.StoreOwnershipType;

public final class CampaignStoreSnapshot {

    private final long storeId;
    private final String storeCode;
    private final StoreOwnershipType ownershipType;
    private final long subsidyFen;
    private final String participationStatus;
    private final long ruleVersion;

    public CampaignStoreSnapshot(long storeId, String storeCode, StoreOwnershipType ownershipType,
                                 long subsidyFen, String participationStatus, long ruleVersion) {
        this.storeId = storeId;
        this.storeCode = storeCode;
        this.ownershipType = ownershipType;
        this.subsidyFen = subsidyFen;
        this.participationStatus = participationStatus;
        this.ruleVersion = ruleVersion;
    }

    public long storeId() { return storeId; }
    public String storeCode() { return storeCode; }
    public StoreOwnershipType ownershipType() { return ownershipType; }
    public long subsidyFen() { return subsidyFen; }
    public String participationStatus() { return participationStatus; }
    public long ruleVersion() { return ruleVersion; }

    /** ACTIVE is retained for records created by early Qinghe fixtures. */
    public boolean participating() {
        return "PARTICIPATING".equals(participationStatus)
                || "ACTIVE".equals(participationStatus);
    }
}
