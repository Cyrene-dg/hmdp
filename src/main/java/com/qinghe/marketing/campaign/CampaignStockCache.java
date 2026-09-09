package com.qinghe.marketing.campaign;

import java.util.OptionalLong;

public interface CampaignStockCache {

    void initialize(long campaignId, long initialStock);

    long applyApprovedIncrease(long campaignId, String adjustmentNo, long incrementStock);

    OptionalLong currentAvailableStock(long campaignId);
}
