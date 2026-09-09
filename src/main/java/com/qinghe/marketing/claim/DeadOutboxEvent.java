package com.qinghe.marketing.claim;

public final class DeadOutboxEvent {

    private final String eventId;
    private final String claimNo;
    private final String lastError;

    public DeadOutboxEvent(String eventId, String claimNo, String lastError) {
        this.eventId = eventId;
        this.claimNo = claimNo;
        this.lastError = lastError;
    }

    public String eventId() { return eventId; }
    public String claimNo() { return claimNo; }
    public String lastError() { return lastError; }
}

