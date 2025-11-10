package com.hmdp.config;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 自定义 Redis 配置：绕开 Redisson，使用 Lettuce 连接工厂
 */
@Configuration
@EnableConfigurationProperties(RedisProperties.class) // 启用 Redis 配置读取（application.yml 中的 redis 配置）
public class RedisConfig {

    /**
     * 1. 构建 Lettuce 连接工厂（核心：替代 RedissonConnectionFactory）
     */
    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory(RedisProperties redisProperties) {
        // 1.1 读取 application.yml 中的 Redis 配置（主机、端口、密码、数据库等）
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProperties.getHost()); // 默认为 localhost
        config.setPort(redisProperties.getPort()); // 默认为 6379
        config.setPassword(redisProperties.getPassword()); // 无密码则为 null
        config.setDatabase(redisProperties.getDatabase()); // 默认为 0

        // 1.2 创建 Lettuce 连接工厂并初始化
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet(); // 必须调用，初始化连接工厂
        return factory;
    }

    /**
     * 2. 构建 StringRedisTemplate（使用 Lettuce 连接工厂）
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();

        // 2.1 绑定 Lettuce 连接工厂（关键：绕开 Redisson 连接）
        template.setConnectionFactory(lettuceConnectionFactory);

        // 2.2 配置序列化（保持 String 序列化，与你原有逻辑一致）
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        // 2.3 初始化模板
        template.afterPropertiesSet();
        return template;
    }
}
