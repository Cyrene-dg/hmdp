package com.qinghe.marketing.redemption;

import com.qinghe.marketing.campaign.CampaignRepository;
import com.qinghe.marketing.campaign.CampaignStoreSnapshot;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.store.StoreOwnershipType;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CampaignStorePolicyTest {

    @Test
    void shouldAcceptParticipationStatusWrittenByCampaignPublication() {
        CampaignRepository campaigns = mock(CampaignRepository.class);
        when(campaigns.findStores(10L)).thenReturn(Collections.singletonList(
                new CampaignStoreSnapshot(2L, "QH-F001", StoreOwnershipType.FRANCHISE,
                        350L, "PARTICIPATING", 1L)));

        CampaignStoreSnapshot applicable = new CampaignStorePolicy(campaigns).requireApplicable(
                10L, new PosAuthenticatedStore(2L, "QH-F001",
                        StoreOwnershipType.FRANCHISE, "POS-QH-F001"));

        assertEquals(350L, applicable.subsidyFen());
    }
}
