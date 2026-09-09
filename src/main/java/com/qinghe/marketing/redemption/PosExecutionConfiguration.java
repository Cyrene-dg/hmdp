package com.qinghe.marketing.redemption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PosExecutionConfiguration {

    @Bean(name = "qinghePosExecutor")
    public ThreadPoolTaskExecutor qinghePosExecutor(
            @Value("${qinghe.pos.execution.core-size:4}") int coreSize,
            @Value("${qinghe.pos.execution.max-size:8}") int maxSize,
            @Value("${qinghe.pos.execution.queue-capacity:64}") int queueCapacity) {
        if (coreSize <= 0 || maxSize < coreSize || queueCapacity < 0) {
            throw new IllegalArgumentException("invalid Qinghe POS executor configuration");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("qinghe-pos-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
