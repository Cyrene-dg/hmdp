package com.qinghe.marketing.claim;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class RabbitClaimOutboxSender implements ClaimOutboxSender {

    private final RabbitTemplate rabbitTemplate;
    private final long confirmTimeoutMillis;
    private final String exchange;
    private final String routingKey;

    @Autowired
    public RabbitClaimOutboxSender(RabbitTemplate rabbitTemplate,
                                   @Value("${qinghe.outbox.publisher.confirm-timeout-ms:5000}")
                                   long confirmTimeoutMillis,
                                   @Value("${qinghe.outbox.publisher.exchange:qinghe.claim.exchange}")
                                   String exchange,
                                   @Value("${qinghe.outbox.publisher.routing-key:qinghe.claim.accepted}")
                                   String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeoutMillis = confirmTimeoutMillis;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public RabbitClaimOutboxSender(RabbitTemplate rabbitTemplate, long confirmTimeoutMillis) {
        this(rabbitTemplate, confirmTimeoutMillis, QingheClaimMessagingConfiguration.EXCHANGE,
                QingheClaimMessagingConfiguration.ROUTING_KEY);
    }

    @Override
    public OutboxPublishResult send(LeasedOutboxEvent event) {
        CorrelationData correlation = new CorrelationData(event.eventId());
        try {
            Message message = MessageBuilder.withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(event.eventId())
                    .setHeader("eventId", event.eventId())
                    .setHeader("eventType", event.eventType())
                    .setHeader("aggregateId", event.aggregateId())
                    .build();
            rabbitTemplate.send(exchange, routingKey, message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                return OutboxPublishResult.failed(confirm.getReason());
            }
            if (correlation.getReturnedMessage() != null) {
                return OutboxPublishResult.failed("message was returned as unroutable");
            }
            return OutboxPublishResult.acknowledged();
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return OutboxPublishResult.failed(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }
}
