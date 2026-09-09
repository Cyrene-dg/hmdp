package com.qinghe.marketing.claim;

import java.time.LocalDateTime;

public final class OutboxEvent {

    private final String eventId;
    private final String aggregateType;
    private final String aggregateId;
    private final String eventType;
    private final long eventVersion;
    private final String payload;
    private final OutboxStatus status;
    private final LocalDateTime createdAt;

    public OutboxEvent(String eventId, String aggregateType, String aggregateId,
                       String eventType, long eventVersion, String payload,
                       OutboxStatus status, LocalDateTime createdAt) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String eventId() { return eventId; }
    public String aggregateType() { return aggregateType; }
    public String aggregateId() { return aggregateId; }
    public String eventType() { return eventType; }
    public long eventVersion() { return eventVersion; }
    public String payload() { return payload; }
    public OutboxStatus status() { return status; }
    public LocalDateTime createdAt() { return createdAt; }
}
