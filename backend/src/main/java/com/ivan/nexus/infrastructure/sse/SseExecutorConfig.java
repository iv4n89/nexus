package com.ivan.nexus.infrastructure.sse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class SseExecutorConfig {

    @Bean(name = "sseExecutor")
    public ThreadPoolTaskExecutor sseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("sse-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "deploymentExecutor")
    public ThreadPoolTaskExecutor deploymentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("deploy-");
        executor.initialize();
        return executor;
    }
}
