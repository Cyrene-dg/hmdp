package com.qinghe.marketing.claim;

public final class LeasedOutboxEvent {

    private final long id;
    private final String eventId;
    private final String aggregateType;
    private final String aggregateId;
    private final String eventType;
    private final long eventVersion;
    private final String payload;
    private final int retryCount;
    private final String leaseOwner;

    public LeasedOutboxEvent(long id, String eventId, String aggregateType, String aggregateId,
                             String eventType, long eventVersion, String payload,
                             int retryCount, String leaseOwner) {
        this.id = id;
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.payload = payload;
        this.retryCount = retryCount;
        this.leaseOwner = leaseOwner;
    }

    public long id() { return id; }
    public String eventId() { return eventId; }
    public String aggregateType() { return aggregateType; }
    public String aggregateId() { return aggregateId; }
    public String eventType() { return eventType; }
    public long eventVersion() { return eventVersion; }
    public String payload() { return payload; }
    public int retryCount() { return retryCount; }
    public String leaseOwner() { return leaseOwner; }
}
