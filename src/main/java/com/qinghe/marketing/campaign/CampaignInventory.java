package com.qinghe.marketing.campaign;

public final class CampaignInventory {

    private final long campaignId;
    private final long totalStock;
    private final long version;

    public CampaignInventory(long campaignId, long totalStock, long version) {
        this.campaignId = campaignId;
        this.totalStock = totalStock;
        this.version = version;
    }

    public long campaignId() { return campaignId; }
    public long totalStock() { return totalStock; }
    public long version() { return version; }
}
