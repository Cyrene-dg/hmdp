package com.qinghe.marketing.persistence;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Real RabbitMQ WP-05 probe proving exhausted delivery is retained in the dead-letter queue. */
class QingheWp05RabbitIT {

    private static final String HOST = System.getProperty("qinghe.it.rabbit.host", "127.0.0.1");
    private static final int PORT = Integer.getInteger("qinghe.it.rabbit.port", 5672);
    private static final String USER = System.getProperty("qinghe.it.rabbit.user", "guest");
    private static final String PASSWORD = credential("qinghe.it.rabbit.password",
            "QINGHE_IT_RABBIT_PASSWORD", "guest");

    @Test
    void shouldRouteRejectedIssueMessageToDurableDeadLetterQueue() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        String exchangeName = "qh.wp05.it.exchange." + suffix;
        String queueName = "qh.wp05.it.queue." + suffix;
        String routingKey = "qh.wp05.it.issue." + suffix;
        String deadExchangeName = "qh.wp05.it.dead.exchange." + suffix;
        String deadQueueName = "qh.wp05.it.dead.queue." + suffix;
        String deadRoutingKey = "qh.wp05.it.dead." + suffix;
        CachingConnectionFactory factory = new CachingConnectionFactory(HOST, PORT);
        factory.setUsername(USER);
        factory.setPassword(PASSWORD);
        RabbitAdmin admin = new RabbitAdmin(factory);
        Channel channel = null;
        try {
            DirectExchange exchange = new DirectExchange(exchangeName, true, false);
            DirectExchange deadExchange = new DirectExchange(deadExchangeName, true, false);
            Queue queue = QueueBuilder.durable(queueName)
                    .deadLetterExchange(deadExchangeName).deadLetterRoutingKey(deadRoutingKey).build();
            Queue deadQueue = QueueBuilder.durable(deadQueueName).build();
            admin.declareExchange(exchange);
            admin.declareExchange(deadExchange);
            admin.declareQueue(queue);
            admin.declareQueue(deadQueue);
            admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(routingKey));
            admin.declareBinding(BindingBuilder.bind(deadQueue).to(deadExchange).with(deadRoutingKey));

            String payload = "{\"eventId\":\"EVT-IT-1\",\"claimNo\":\"CLM-IT-1\"}";
            Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT).setMessageId("EVT-IT-1").build();
            new RabbitTemplate(factory).send(exchangeName, routingKey, message);

            channel = factory.createConnection().createChannel(false);
            GetResponse delivery = channel.basicGet(queueName, false);
            assertNotNull(delivery);
            channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);

            Message dead = new RabbitTemplate(factory).receive(deadQueueName, 3000L);
            assertNotNull(dead);
            assertEquals(payload, new String(dead.getBody(), StandardCharsets.UTF_8));
            assertEquals("EVT-IT-1", dead.getMessageProperties().getMessageId());
        } finally {
            if (channel != null) channel.close();
            try {
                admin.deleteQueue(queueName);
                admin.deleteQueue(deadQueueName);
                admin.deleteExchange(exchangeName);
                admin.deleteExchange(deadExchangeName);
            } finally {
                factory.destroy();
            }
        }
    }

    private static String credential(String propertyName, String environmentName, String fallback) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) return propertyValue;
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null || environmentValue.trim().isEmpty() ? fallback : environmentValue;
    }
}
