package com.qinghe.marketing.entitlement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "qinghe.claim.failure-recovery.enabled", havingValue = "true")
public class ClaimFailureRecoveryScheduler {

    private final ClaimFailureRecoveryService recoveryService;
    private final int batchSize;
    private final Duration compensatingAge;

    public ClaimFailureRecoveryScheduler(ClaimFailureRecoveryService recoveryService,
                                         @Value("${qinghe.claim.failure-recovery.batch-size:50}") int batchSize,
                                         @Value("${qinghe.claim.failure-recovery.compensating-seconds:30}")
                                         long compensatingSeconds) {
        this.recoveryService = recoveryService;
        this.batchSize = batchSize;
        this.compensatingAge = Duration.ofSeconds(compensatingSeconds);
    }

    @Scheduled(fixedDelayString = "${qinghe.claim.failure-recovery.scan-delay-ms:5000}")
    public void recover() {
        recoveryService.recoverDeadOutbox(batchSize);
        recoveryService.recoverStuckCompensations(compensatingAge, batchSize);
    }
}
