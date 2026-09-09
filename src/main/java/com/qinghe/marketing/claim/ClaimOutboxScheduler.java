package com.qinghe.marketing.claim;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "qinghe.outbox.publisher.enabled", havingValue = "true")
public class ClaimOutboxScheduler {

    private final ClaimOutboxPublishingService publisher;
    private final String instanceId;
    private final int batchSize;
    private final Duration leaseDuration;
    private final int maxRetries;

    public ClaimOutboxScheduler(ClaimOutboxPublishingService publisher,
                                @Value("${qinghe.outbox.publisher.instance-id:}") String configuredInstanceId,
                                @Value("${qinghe.outbox.publisher.batch-size:50}") int batchSize,
                                @Value("${qinghe.outbox.publisher.lease-seconds:30}") long leaseSeconds,
                                @Value("${qinghe.outbox.publisher.max-retries:8}") int maxRetries) {
        this.publisher = publisher;
        this.instanceId = configuredInstanceId == null || configuredInstanceId.trim().isEmpty()
                ? "outbox-" + UUID.randomUUID() : configuredInstanceId;
        this.batchSize = batchSize;
        this.leaseDuration = Duration.ofSeconds(leaseSeconds);
        this.maxRetries = maxRetries;
    }

    @Scheduled(fixedDelayString = "${qinghe.outbox.publisher.scan-delay-ms:1000}")
    public void publish() {
        publisher.publishBatch(instanceId, batchSize, leaseDuration, maxRetries);
    }
}
