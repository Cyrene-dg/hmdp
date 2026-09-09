package com.qinghe.marketing.claim;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(name = "qinghe.claim.recovery.enabled", havingValue = "true")
public class ClaimReservationRecoveryScheduler {

    private final ClaimRecoveryCampaignSource campaigns;
    private final ClaimReservationRecoveryService recoveryService;
    private final Duration orphanAge;
    private final int campaignPageSize;
    private final int reservationBatchSize;

    public ClaimReservationRecoveryScheduler(
            ClaimRecoveryCampaignSource campaigns,
            ClaimReservationRecoveryService recoveryService,
            @Value("${qinghe.claim.recovery.orphan-seconds:120}") long orphanSeconds,
            @Value("${qinghe.claim.recovery.campaign-page-size:100}") int campaignPageSize,
            @Value("${qinghe.claim.recovery.reservation-batch-size:100}") int reservationBatchSize) {
        this.campaigns = campaigns;
        this.recoveryService = recoveryService;
        this.orphanAge = Duration.ofSeconds(orphanSeconds);
        this.campaignPageSize = campaignPageSize;
        this.reservationBatchSize = reservationBatchSize;
    }

    @Scheduled(fixedDelayString = "${qinghe.claim.recovery.scan-delay-ms:30000}")
    public void recover() {
        int offset = 0;
        while (true) {
            List<Long> campaignIds = campaigns.findCampaignIds(offset, campaignPageSize);
            for (Long campaignId : campaignIds) {
                recoveryService.recoverCampaign(campaignId, orphanAge, reservationBatchSize);
            }
            if (campaignIds.size() < campaignPageSize) {
                return;
            }
            offset += campaignIds.size();
        }
    }
}
