package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.claim.ClaimReservationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ClaimIssueService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClaimIssueService.class);

    private final ClaimIssueTransactionService transactionService;
    private final ClaimReservationStore reservationStore;

    public ClaimIssueService(ClaimIssueTransactionService transactionService,
                             ClaimReservationStore reservationStore) {
        this.transactionService = transactionService;
        this.reservationStore = reservationStore;
    }

    public ClaimIssueResult issue(BenefitIssueCommand command) {
        ClaimIssueResult result = transactionService.issue(command);
        if (result.outcome() == ClaimIssueOutcome.ISSUED
                || result.outcome() == ClaimIssueOutcome.ALREADY_ISSUED) {
            try {
                reservationStore.markIssued(command.campaignId(), command.reservationId(),
                        result.entitlement().entitlementNo());
            } catch (RuntimeException redisFailure) {
                LOGGER.warn("Entitlement exists but reservation marker update failed, claimNo={}",
                        command.claimNo(), redisFailure);
            }
        }
        return result;
    }
}
