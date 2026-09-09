package com.qinghe.marketing.persistence;

import com.qinghe.marketing.claim.LeasedOutboxEvent;
import com.qinghe.marketing.claim.OutboxPublishResult;
import com.qinghe.marketing.claim.RabbitClaimOutboxSender;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real RabbitMQ publisher-confirm probe using uniquely named, explicitly deleted resources. */
class QingheWp04RabbitIT {

    private static final String HOST = System.getProperty("qinghe.it.rabbit.host", "127.0.0.1");
    private static final int PORT = Integer.getInteger("qinghe.it.rabbit.port", 5672);
    private static final String USER = System.getProperty("qinghe.it.rabbit.user", "guest");
    private static final String PASSWORD = credential("qinghe.it.rabbit.password", "QINGHE_IT_RABBIT_PASSWORD", "guest");

    @Test
    void shouldReceiveBrokerAckForPersistentRoutableJsonMessage() {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        String exchangeName = "qh.wp04.it.exchange." + suffix;
        String queueName = "qh.wp04.it.queue." + suffix;
        String routingKey = "qh.wp04.it.accepted." + suffix;
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(HOST, PORT);
        connectionFactory.setUsername(USER);
        connectionFactory.setPassword(PASSWORD);
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        try {
            DirectExchange exchange = new DirectExchange(exchangeName, false, true);
            Queue queue = new Queue(queueName, false, false, true);
            Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
            admin.declareExchange(exchange);
            admin.declareQueue(queue);
            admin.declareBinding(binding);

            RabbitTemplate rabbit = new RabbitTemplate(connectionFactory);
            rabbit.setMandatory(true);
            RabbitClaimOutboxSender sender = new RabbitClaimOutboxSender(
                    rabbit, 3000L, exchangeName, routingKey);
            String payload = "{\"eventId\":\"EVT-IT-1\",\"claimNo\":\"CLM-IT-1\"}";

            OutboxPublishResult result = sender.send(new LeasedOutboxEvent(
                    1L, "EVT-IT-1", "CLAIM_REQUEST", "CLM-IT-1", "CLAIM_ACCEPTED",
                    1L, payload, 0, "instance-it"));
            Message received = rabbit.receive(queueName, 3000L);

            assertTrue(result.isAcknowledged());
            assertNotNull(received);
            assertEquals(payload, new String(received.getBody(), StandardCharsets.UTF_8));
            assertEquals("EVT-IT-1", received.getMessageProperties().getMessageId());
        } finally {
            try {
                admin.deleteQueue(queueName);
                admin.deleteExchange(exchangeName);
            } finally {
                connectionFactory.destroy();
            }
        }
    }

    private static String credential(String propertyName, String environmentName, String fallback) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) {
            return propertyValue;
        }
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null || environmentValue.trim().isEmpty() ? fallback : environmentValue;
    }
}
