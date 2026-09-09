package com.qinghe.marketing.claim;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "qinghe.outbox.publisher.enabled", havingValue = "true")
public class QingheClaimMessagingConfiguration {

    public static final String EXCHANGE = "qinghe.claim.exchange";
    public static final String QUEUE = "qinghe.claim.issue.queue";
    public static final String ROUTING_KEY = "qinghe.claim.accepted";

    private final String exchange;
    private final String queue;
    private final String routingKey;

    public QingheClaimMessagingConfiguration(
            @Value("${qinghe.outbox.publisher.exchange:qinghe.claim.exchange}") String exchange,
            @Value("${qinghe.outbox.publisher.queue:qinghe.claim.issue.queue}") String queue,
            @Value("${qinghe.outbox.publisher.routing-key:qinghe.claim.accepted}") String routingKey) {
        this.exchange = exchange;
        this.queue = queue;
        this.routingKey = routingKey;
    }

    @Bean
    public DirectExchange qingheClaimExchange() {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public Queue qingheClaimIssueQueue() {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    public Binding qingheClaimIssueBinding(Queue qingheClaimIssueQueue,
                                           DirectExchange qingheClaimExchange) {
        return BindingBuilder.bind(qingheClaimIssueQueue).to(qingheClaimExchange).with(routingKey);
    }
}
