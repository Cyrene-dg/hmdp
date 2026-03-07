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

    //消息转换器
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    //持久化秒杀业务交换机
    @Bean
    public DirectExchange seckillOrderExchange() {
        return new DirectExchange(RabbitMqConstants.SECKILL_ORDER_EXCHANGE, true, false);
    }

    //创建秒杀订单队列
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(RabbitMqConstants.SECKILL_ORDER_QUEUE)//创建一个名称是seckill.order.queue的持久化队列
                .deadLetterExchange(RabbitMqConstants.SECKILL_ORDER_DLX_EXCHANGE)//指定死信交换机seckill.order.dlx.exchange
                .deadLetterRoutingKey(RabbitMqConstants.SECKILL_ORDER_DLX_ROUTING_KEY)//指定这条死信消息发往死信交换机时使用的路由键。
                .build();
    }

    //绑定主队列和主交换机
    @Bean
    //这个方法的参数就是队列和交换机，已经在容器里里面注册了
    public Binding seckillOrderBinding(Queue seckillOrderQueue, DirectExchange seckillOrderExchange) {
        return BindingBuilder.bind(seckillOrderQueue)
                .to(seckillOrderExchange)
                .with(RabbitMqConstants.SECKILL_ORDER_ROUTING_KEY);
    }

    //new一个死信交换机
    @Bean
    public DirectExchange seckillOrderDlxExchange() {
        return new DirectExchange(RabbitMqConstants.SECKILL_ORDER_DLX_EXCHANGE, true, false);
    }

    //new一个死信队列
    @Bean
    public Queue seckillOrderDlxQueue() {
        return QueueBuilder.durable(RabbitMqConstants.SECKILL_ORDER_DLX_QUEUE).build();
    }

    //绑定死信交换机和死信队列
    @Bean
    public Binding seckillOrderDlxBinding(Queue seckillOrderDlxQueue, DirectExchange seckillOrderDlxExchange) {
        return BindingBuilder.bind(seckillOrderDlxQueue)
                .to(seckillOrderDlxExchange)
                .with(RabbitMqConstants.SECKILL_ORDER_DLX_ROUTING_KEY);
    }
}
