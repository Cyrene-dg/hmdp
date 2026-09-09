package com.qinghe.marketing.campaign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CampaignStateScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CampaignStateScheduler.class);
    private final CampaignService campaignService;

    public CampaignStateScheduler(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @Scheduled(fixedDelayString = "${qinghe.campaign.state-scan-delay-ms:30000}")
    public void advance() {
        int transitions = campaignService.advanceTimeBasedStates();
        if (transitions > 0) {
            LOGGER.info("Advanced {} Qinghe campaign time-based state(s)", transitions);
        }
    }
}
