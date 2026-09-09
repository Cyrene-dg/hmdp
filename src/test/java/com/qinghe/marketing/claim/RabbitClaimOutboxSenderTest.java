package com.qinghe.marketing.claim;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitClaimOutboxSenderTest {

    @Test
    void shouldSendPersistentRawJsonAndRequirePublisherAck() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq(QingheClaimMessagingConfiguration.EXCHANGE),
                eq(QingheClaimMessagingConfiguration.ROUTING_KEY),
                any(Message.class), any(CorrelationData.class));
        RabbitClaimOutboxSender sender = new RabbitClaimOutboxSender(rabbit, 1000L);

        OutboxPublishResult result = sender.send(new LeasedOutboxEvent(
                1L, "EVT-1", "CLAIM_REQUEST", "CLM-1", "CLAIM_ACCEPTED",
                1L, "{\"claimNo\":\"CLM-1\"}", 0, "instance-a"));

        assertTrue(result.isAcknowledged());
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq(QingheClaimMessagingConfiguration.EXCHANGE),
                eq(QingheClaimMessagingConfiguration.ROUTING_KEY),
                message.capture(), any(CorrelationData.class));
        assertEquals("{\"claimNo\":\"CLM-1\"}",
                new String(message.getValue().getBody(), StandardCharsets.UTF_8));
        assertEquals("application/json", message.getValue().getMessageProperties().getContentType());
        assertEquals("EVT-1", message.getValue().getMessageProperties().getMessageId());
    }
}
