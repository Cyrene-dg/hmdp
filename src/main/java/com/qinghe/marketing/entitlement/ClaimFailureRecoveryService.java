package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.claim.ClaimRequest;
import com.qinghe.marketing.claim.ClaimRequestRepository;
import com.qinghe.marketing.claim.DeadOutboxEvent;
import com.qinghe.marketing.claim.OutboxEventRepository;
import com.qinghe.marketing.shared.clock.BusinessClock;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class ClaimFailureRecoveryService {

    private final OutboxEventRepository outboxRepository;
    private final ClaimRequestRepository claimRepository;
    private final ClaimFailureService failureService;
    private final BusinessClock clock;

    public ClaimFailureRecoveryService(OutboxEventRepository outboxRepository,
                                       ClaimRequestRepository claimRepository,
                                       ClaimFailureService failureService,
                                       BusinessClock clock) {
        this.outboxRepository = outboxRepository;
        this.claimRepository = claimRepository;
        this.failureService = failureService;
        this.clock = clock;
    }

    public int recoverDeadOutbox(int limit) {
        List<DeadOutboxEvent> events = outboxRepository.findUnhandledDead(limit);
        for (DeadOutboxEvent event : events) {
            failureService.fail(event.eventId(), event.claimNo(), "OUTBOX_DEAD");
        }
        return events.size();
    }

    public int recoverStuckCompensations(Duration age, int limit) {
        List<ClaimRequest> claims = claimRepository.findCompensatingBefore(
                clock.dateTime().minus(age), limit);
        for (ClaimRequest claim : claims) {
            failureService.fail(null, claim.claimNo(), claim.failureCode());
        }
        return claims.size();
    }
}

