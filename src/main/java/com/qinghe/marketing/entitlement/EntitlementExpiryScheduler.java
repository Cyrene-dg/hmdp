package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "qinghe.entitlement.expiry.enabled", havingValue = "true")
public class EntitlementExpiryScheduler {

    private final MemberEntitlementRepository repository;
    private final BusinessClock clock;

    public EntitlementExpiryScheduler(MemberEntitlementRepository repository, BusinessClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${qinghe.entitlement.expiry.scan-delay-ms:60000}")
    public void expire() {
        repository.expireAvailableBefore(clock.dateTime());
    }
}
