package com.hmdp.config;

import com.hmdp.utils.RabbitMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange seckillOrderExchange() {
        return new DirectExchange(RabbitMqConstants.SECKILL_ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(RabbitMqConstants.SECKILL_ORDER_QUEUE)
                .deadLetterExchange(RabbitMqConstants.SECKILL_ORDER_DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqConstants.SECKILL_ORDER_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding seckillOrderBinding(Queue seckillOrderQueue, DirectExchange seckillOrderExchange) {
        return BindingBuilder.bind(seckillOrderQueue)
                .to(seckillOrderExchange)
                .with(RabbitMqConstants.SECKILL_ORDER_ROUTING_KEY);
    }

    @Bean
    public DirectExchange seckillOrderDlxExchange() {
        return new DirectExchange(RabbitMqConstants.SECKILL_ORDER_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillOrderDlxQueue() {
        return QueueBuilder.durable(RabbitMqConstants.SECKILL_ORDER_DLX_QUEUE).build();
    }

    @Bean
    public Binding seckillOrderDlxBinding(Queue seckillOrderDlxQueue, DirectExchange seckillOrderDlxExchange) {
        return BindingBuilder.bind(seckillOrderDlxQueue)
                .to(seckillOrderDlxExchange)
                .with(RabbitMqConstants.SECKILL_ORDER_DLX_ROUTING_KEY);
    }
}
