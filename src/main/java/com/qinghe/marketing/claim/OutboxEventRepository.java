package com.qinghe.marketing.claim;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collections;

public interface OutboxEventRepository {

    void insert(OutboxEvent event);

    List<LeasedOutboxEvent> leaseBatch(String leaseOwner, LocalDateTime now,
                                       LocalDateTime leaseUntil, int limit);

    OutboxStatus completePublication(String eventId, String leaseOwner, boolean acknowledged,
                                     String errorMessage, LocalDateTime nextRetryAt,
                                     int maxRetries, LocalDateTime now);

    default List<DeadOutboxEvent> findUnhandledDead(int limit) {
        return Collections.emptyList();
    }
}
