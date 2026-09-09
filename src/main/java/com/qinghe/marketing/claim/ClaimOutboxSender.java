package com.qinghe.marketing.claim;

public interface ClaimOutboxSender {

    OutboxPublishResult send(LeasedOutboxEvent event);
}
