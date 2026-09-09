package com.qinghe.marketing.campaign;

import org.springframework.stereotype.Service;

import java.util.OptionalLong;

@Service
public class CampaignStockConsistencyService {

    private final CampaignRepository campaignRepository;
    private final CampaignStockCache stockCache;

    public CampaignStockConsistencyService(CampaignRepository campaignRepository,
                                           CampaignStockCache stockCache) {
        this.campaignRepository = campaignRepository;
        this.stockCache = stockCache;
    }

    public CampaignStockConsistency inspect(long campaignId) {
        CampaignInventory inventory = campaignRepository.requireInventory(campaignId);
        OptionalLong redis = stockCache.currentAvailableStock(campaignId);
        return new CampaignStockConsistency(campaignId, inventory.totalStock(),
                redis.isPresent() ? redis.getAsLong() : null, redis.isPresent());
    }
}
