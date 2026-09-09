package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.claim.ClaimRequest;
import com.qinghe.marketing.claim.ClaimReservationStore;
import org.springframework.stereotype.Service;

@Service
public class ClaimFailureService {

    private final ClaimFailureTransactionService transactionService;
    private final ClaimReservationStore reservationStore;

    public ClaimFailureService(ClaimFailureTransactionService transactionService,
                               ClaimReservationStore reservationStore) {
        this.transactionService = transactionService;
        this.reservationStore = reservationStore;
    }

    public void fail(String eventId, String claimNo, String failureCode) {
        String stableCode = normalize(failureCode);
        CompensationClaim selected = transactionService.begin(claimNo, stableCode);
        if (!selected.actionable()) {
            transactionService.recordIgnored(eventId, selected.claim());
            return;
        }
        ClaimRequest claim = selected.claim();
        reservationStore.compensate(claim.campaignId(), claim.memberId(), claim.requestId(),
                claim.reservationId(), stableCode);
        transactionService.complete(claim, eventId, stableCode);
    }

    private static String normalize(String failureCode) {
        if (failureCode == null || failureCode.trim().isEmpty()) {
            return "ISSUE_RETRY_EXHAUSTED";
        }
        String value = failureCode.trim().toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9_:-]", "_");
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}

