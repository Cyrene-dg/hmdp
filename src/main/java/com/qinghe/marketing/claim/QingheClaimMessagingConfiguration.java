package com.qinghe.marketing.claim;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("'${qinghe.outbox.publisher.enabled:false}' == 'true' "
        + "or '${qinghe.claim.consumer.enabled:false}' == 'true'")
public class QingheClaimMessagingConfiguration {

    public static final String EXCHANGE = "qinghe.claim.exchange";
    public static final String QUEUE = "qinghe.claim.issue.v1.queue";
    public static final String ROUTING_KEY = "qinghe.claim.accepted";
    public static final String DEAD_EXCHANGE = "qinghe.claim.dead.exchange";
    public static final String DEAD_QUEUE = "qinghe.claim.issue.v1.dead.queue";
    public static final String DEAD_ROUTING_KEY = "qinghe.claim.issue.dead";
    public static final String PARKING_EXCHANGE = "qinghe.claim.parking.exchange";
    public static final String PARKING_QUEUE = "qinghe.claim.issue.v1.parking.queue";
    public static final String PARKING_ROUTING_KEY = "qinghe.claim.issue.parking";

    private final String exchange;
    private final String queue;
    private final String routingKey;
    private final String deadExchange;
    private final String deadQueue;
    private final String deadRoutingKey;
    private final String parkingExchange;
    private final String parkingQueue;
    private final String parkingRoutingKey;

    public QingheClaimMessagingConfiguration(
            @Value("${qinghe.outbox.publisher.exchange:qinghe.claim.exchange}") String exchange,
            @Value("${qinghe.outbox.publisher.queue:qinghe.claim.issue.v1.queue}") String queue,
            @Value("${qinghe.outbox.publisher.routing-key:qinghe.claim.accepted}") String routingKey,
            @Value("${qinghe.claim.consumer.dead-exchange:qinghe.claim.dead.exchange}") String deadExchange,
            @Value("${qinghe.claim.consumer.dead-queue:qinghe.claim.issue.v1.dead.queue}") String deadQueue,
            @Value("${qinghe.claim.consumer.dead-routing-key:qinghe.claim.issue.dead}") String deadRoutingKey,
            @Value("${qinghe.claim.consumer.parking-exchange:qinghe.claim.parking.exchange}") String parkingExchange,
            @Value("${qinghe.claim.consumer.parking-queue:qinghe.claim.issue.v1.parking.queue}") String parkingQueue,
            @Value("${qinghe.claim.consumer.parking-routing-key:qinghe.claim.issue.parking}") String parkingRoutingKey) {
        this.exchange = exchange;
        this.queue = queue;
        this.routingKey = routingKey;
        this.deadExchange = deadExchange;
        this.deadQueue = deadQueue;
        this.deadRoutingKey = deadRoutingKey;
        this.parkingExchange = parkingExchange;
        this.parkingQueue = parkingQueue;
        this.parkingRoutingKey = parkingRoutingKey;
    }

    @Bean
    public DirectExchange qingheClaimExchange() {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public Queue qingheClaimIssueQueue() {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadExchange)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }

    @Bean
    public Binding qingheClaimIssueBinding(@Qualifier("qingheClaimIssueQueue") Queue qingheClaimIssueQueue,
                                           @Qualifier("qingheClaimExchange") DirectExchange qingheClaimExchange) {
        return BindingBuilder.bind(qingheClaimIssueQueue).to(qingheClaimExchange).with(routingKey);
    }

    @Bean
    public DirectExchange qingheClaimDeadExchange() {
        return new DirectExchange(deadExchange, true, false);
    }

    @Bean
    public Queue qingheClaimIssueDeadQueue() {
        return QueueBuilder.durable(deadQueue)
                .deadLetterExchange(parkingExchange)
                .deadLetterRoutingKey(parkingRoutingKey)
                .build();
    }

    @Bean
    public Binding qingheClaimIssueDeadBinding(
            @Qualifier("qingheClaimIssueDeadQueue") Queue qingheClaimIssueDeadQueue,
            @Qualifier("qingheClaimDeadExchange") DirectExchange qingheClaimDeadExchange) {
        return BindingBuilder.bind(qingheClaimIssueDeadQueue)
                .to(qingheClaimDeadExchange).with(deadRoutingKey);
    }

    @Bean
    public DirectExchange qingheClaimParkingExchange() {
        return new DirectExchange(parkingExchange, true, false);
    }

    @Bean
    public Queue qingheClaimIssueParkingQueue() {
        return QueueBuilder.durable(parkingQueue).build();
    }

    @Bean
    public Binding qingheClaimIssueParkingBinding(
            @Qualifier("qingheClaimIssueParkingQueue") Queue qingheClaimIssueParkingQueue,
            @Qualifier("qingheClaimParkingExchange") DirectExchange qingheClaimParkingExchange) {
        return BindingBuilder.bind(qingheClaimIssueParkingQueue)
                .to(qingheClaimParkingExchange).with(parkingRoutingKey);
    }

    @Bean(name = "qingheClaimListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory qingheClaimListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Value("${qinghe.claim.consumer.max-attempts:4}") int maxAttempts,
            @Value("${qinghe.claim.consumer.retry-initial-ms:200}") long initialInterval,
            @Value("${qinghe.claim.consumer.retry-max-ms:2000}") long maxInterval) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .backOffOptions(initialInterval, 2.0, maxInterval)
                .build());
        return factory;
    }
}
