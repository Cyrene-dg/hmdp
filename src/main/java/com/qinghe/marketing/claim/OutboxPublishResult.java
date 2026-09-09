package com.qinghe.marketing.claim;

public final class OutboxPublishResult {

    private final boolean acknowledged;
    private final String error;

    private OutboxPublishResult(boolean acknowledged, String error) {
        this.acknowledged = acknowledged;
        this.error = error;
    }

    public static OutboxPublishResult acknowledged() {
        return new OutboxPublishResult(true, null);
    }

    public static OutboxPublishResult failed(String error) {
        return new OutboxPublishResult(false, error == null ? "publisher did not acknowledge" : error);
    }

    public boolean isAcknowledged() { return acknowledged; }
    public String error() { return error; }
}
