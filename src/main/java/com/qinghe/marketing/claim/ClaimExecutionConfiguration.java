package com.qinghe.marketing.claim;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ClaimExecutionConfiguration {

    @Bean(name = "qingheClaimExecutor")
    public ThreadPoolTaskExecutor qingheClaimExecutor(
            @Value("${qinghe.claim.execution.core-size:8}") int coreSize,
            @Value("${qinghe.claim.execution.max-size:16}") int maxSize,
            @Value("${qinghe.claim.execution.queue-capacity:128}") int queueCapacity) {
        if (coreSize <= 0 || maxSize < coreSize || queueCapacity < 0) {
            throw new IllegalArgumentException("invalid Qinghe claim executor configuration");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("qinghe-claim-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
