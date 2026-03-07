package com.hmdp.utils;

public final class RabbitMqConstants {

    private RabbitMqConstants() {
    }

    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    public static final String SECKILL_ORDER_DLX_EXCHANGE = "seckill.order.dlx.exchange";
    public static final String SECKILL_ORDER_DLX_QUEUE = "seckill.order.dlx.queue";
    public static final String SECKILL_ORDER_DLX_ROUTING_KEY = "seckill.order.dlx";
}
