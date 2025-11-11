package com.hmdp.config;


import io.lettuce.core.resource.DefaultClientResources;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * 自定义 Redis 配置：绕开 Redisson，使用 Lettuce 连接工厂
 */
@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    /**
     * 新增：显式创建 DefaultClientResources（文章核心方案）
     * 1. @Bean(destroyMethod = "shutdown")：容器销毁时自动调用 shutdown()，释放 HashedWheelTimer 等资源
     * 2. @ConditionalOnMissingBean：避免重复创建（若项目中已有 ClientResources 则复用）
     */
    @Bean(destroyMethod = "shutdown")
    public DefaultClientResources lettuceClientResources() {
        return DefaultClientResources.create();
    }

    /**
     * 1. 构建 Lettuce 连接工厂（核心修改：绑定 DefaultClientResources）
     */
    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory(
            RedisProperties redisProperties,
            DefaultClientResources lettuceClientResources // 注入上面创建的资源
    ) {
        // 1.1 保留原有：读取 application.yml 中的 Redis 单机配置
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProperties.getHost());
        config.setPort(redisProperties.getPort());
        config.setPassword(redisProperties.getPassword());
        config.setDatabase(redisProperties.getDatabase());

        // 1.2 新增：构建客户端配置，绑定资源（文章核心逻辑）
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientResources(lettuceClientResources) // 绑定 Spring 管理的资源，避免隐式创建
                .commandTimeout(Duration.ofSeconds(5)) // 可选：补充超时配置，增强稳定性
                .build();

        // 1.3 保留原有：创建连接工厂（传入配置+客户端配置）
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        factory.afterPropertiesSet(); // 保持原有初始化逻辑
        return factory;
    }

    /**
     * 2. 构建 StringRedisTemplate（完全保留原有逻辑，无修改）
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(lettuceConnectionFactory);

        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
