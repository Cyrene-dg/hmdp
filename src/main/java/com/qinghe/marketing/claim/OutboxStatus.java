package com.qinghe.marketing.claim;

public enum OutboxStatus {
    NEW,
    PUBLISHING,
    PUBLISHED,
    RETRY,
    DEAD
}
