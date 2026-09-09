package com.qinghe.marketing.claim;

import java.util.List;

public interface ClaimRecoveryCampaignSource {

    List<Long> findCampaignIds(int offset, int limit);
}
