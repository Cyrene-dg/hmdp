package com.qinghe.marketing.campaign;

public final class CampaignStockConsistency {

    private final long campaignId;
    private final long databaseTotalStock;
    private final Long redisAvailableStock;
    private final boolean initialized;

    public CampaignStockConsistency(long campaignId, long databaseTotalStock,
                                    Long redisAvailableStock, boolean initialized) {
        this.campaignId = campaignId;
        this.databaseTotalStock = databaseTotalStock;
        this.redisAvailableStock = redisAvailableStock;
        this.initialized = initialized;
    }

    public long campaignId() { return campaignId; }
    public long databaseTotalStock() { return databaseTotalStock; }
    public Long redisAvailableStock() { return redisAvailableStock; }
    public boolean initialized() { return initialized; }
}
