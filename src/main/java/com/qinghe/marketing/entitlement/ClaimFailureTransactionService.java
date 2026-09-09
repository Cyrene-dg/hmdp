package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.claim.ClaimRequest;
import com.qinghe.marketing.claim.ClaimRequestRepository;
import com.qinghe.marketing.claim.ClaimStatus;
import com.qinghe.marketing.shared.clock.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimFailureTransactionService {

    private final ClaimRequestRepository claimRepository;
    private final ClaimIssueDeliveryRepository deliveryRepository;
    private final BusinessClock clock;

    public ClaimFailureTransactionService(ClaimRequestRepository claimRepository,
                                          ClaimIssueDeliveryRepository deliveryRepository,
                                          BusinessClock clock) {
        this.claimRepository = claimRepository;
        this.deliveryRepository = deliveryRepository;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public CompensationClaim begin(String claimNo, String failureCode) {
        ClaimRequest claim = claimRepository.findByClaimNoForUpdate(claimNo)
                .orElseThrow(() -> new IllegalArgumentException("claim failure target does not exist"));
        if (claim.status() == ClaimStatus.PROCESSING) {
            if (!claimRepository.beginCompensation(claim.id(), claim.version(), failureCode,
                    clock.dateTime())) {
                throw new IllegalStateException("claim compensation transition lost its state gate");
            }
            ClaimRequest compensating = claimRepository.findByClaimNoForUpdate(claimNo)
                    .orElseThrow(() -> new IllegalStateException("compensating claim disappeared"));
            return new CompensationClaim(compensating, true);
        }
        return new CompensationClaim(claim, claim.status() == ClaimStatus.COMPENSATING);
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(ClaimRequest compensating, String eventId, String failureCode) {
        ClaimRequest current = claimRepository.findByClaimNoForUpdate(compensating.claimNo())
                .orElseThrow(() -> new IllegalStateException("compensating claim disappeared"));
        if (current.status() == ClaimStatus.FAILED) {
            recordIfPossible(eventId, current.id(), "FAILED", current.failureCode());
            return;
        }
        if (current.status() != ClaimStatus.COMPENSATING
                || !claimRepository.markFailed(current.id(), current.version(), failureCode,
                clock.dateTime())) {
            throw new IllegalStateException("claim failure transition lost its state gate");
        }
        recordIfPossible(eventId, current.id(), "FAILED", failureCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordIgnored(String eventId, ClaimRequest claim) {
        recordIfPossible(eventId, claim.id(), "IGNORED_TERMINAL", claim.failureCode());
    }

    private void recordIfPossible(String eventId, long claimId, String outcome, String failureCode) {
        if (eventId != null && !eventId.trim().isEmpty()) {
            deliveryRepository.record(eventId, claimId, outcome, failureCode, clock.dateTime());
        }
    }
}

