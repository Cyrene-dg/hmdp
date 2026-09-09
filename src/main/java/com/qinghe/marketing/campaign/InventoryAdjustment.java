package com.qinghe.marketing.campaign;

public final class InventoryAdjustment {

    private final long id;
    private final String adjustmentNo;
    private final long campaignId;
    private final long incrementStock;
    private final InventoryAdjustmentStatus status;
    private final String reason;
    private final String applicantId;
    private final String approverId;
    private final long version;

    public InventoryAdjustment(long id, String adjustmentNo, long campaignId, long incrementStock,
                               InventoryAdjustmentStatus status, String reason, String applicantId,
                               String approverId, long version) {
        this.id = id;
        this.adjustmentNo = adjustmentNo;
        this.campaignId = campaignId;
        this.incrementStock = incrementStock;
        this.status = status;
        this.reason = reason;
        this.applicantId = applicantId;
        this.approverId = approverId;
        this.version = version;
    }

    public long id() { return id; }
    public String adjustmentNo() { return adjustmentNo; }
    public long campaignId() { return campaignId; }
    public long incrementStock() { return incrementStock; }
    public InventoryAdjustmentStatus status() { return status; }
    public String reason() { return reason; }
    public String applicantId() { return applicantId; }
    public String approverId() { return approverId; }
    public long version() { return version; }
}
