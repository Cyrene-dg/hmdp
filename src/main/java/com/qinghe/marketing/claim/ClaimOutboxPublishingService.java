package com.qinghe.marketing.claim;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClaimOutboxPublishingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClaimOutboxPublishingService.class);

    private final OutboxEventRepository repository;
    private final ClaimOutboxSender sender;
    private final BusinessClock clock;

    public ClaimOutboxPublishingService(OutboxEventRepository repository,
                                        ClaimOutboxSender sender, BusinessClock clock) {
        this.repository = repository;
        this.sender = sender;
        this.clock = clock;
    }

    public int publishBatch(String instanceId, int batchSize, Duration leaseDuration,
                            int maxRetries) {
        LocalDateTime now = clock.dateTime();
        List<LeasedOutboxEvent> events = repository.leaseBatch(
                instanceId, now, now.plus(leaseDuration), batchSize);
        for (LeasedOutboxEvent event : events) {
            OutboxPublishResult result = sender.send(event);
            LocalDateTime completedAt = clock.dateTime();
            LocalDateTime nextRetryAt = completedAt.plus(retryDelay(event.retryCount() + 1));
            OutboxStatus status = repository.completePublication(event.eventId(), instanceId,
                    result.isAcknowledged(), result.error(), nextRetryAt, maxRetries, completedAt);
            if (status == OutboxStatus.DEAD) {
                LOGGER.error("Claim outbox reached DEAD, eventId={}", event.eventId());
            }
        }
        return events.size();
    }

    static Duration retryDelay(int failureCount) {
        long seconds = Math.min(300L, 1L << Math.min(9, Math.max(0, failureCount - 1)));
        return Duration.ofSeconds(seconds);
    }
}
