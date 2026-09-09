package com.qinghe.marketing.redemption;

import com.qinghe.marketing.campaign.CampaignRepository;
import com.qinghe.marketing.campaign.CampaignStoreSnapshot;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Component;

@Component
public class CampaignStorePolicy {

    private final CampaignRepository campaignRepository;

    public CampaignStorePolicy(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    public CampaignStoreSnapshot requireApplicable(long campaignId,
                                                    PosAuthenticatedStore store) {
        return campaignRepository.findStores(campaignId).stream()
                .filter(snapshot -> snapshot.storeId() == store.storeId())
                .filter(snapshot -> snapshot.storeCode().equals(store.storeCode()))
                .filter(snapshot -> "ACTIVE".equals(snapshot.participationStatus()))
                .findFirst()
                .orElseThrow(() -> new QingheBusinessException(
                        QingheErrorCode.STORE_NOT_APPLICABLE,
                        "right is not applicable to the authenticated store"));
    }
}
