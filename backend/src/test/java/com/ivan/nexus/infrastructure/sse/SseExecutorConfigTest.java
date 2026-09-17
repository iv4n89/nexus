package com.ivan.nexus.infrastructure.sse;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class SseExecutorConfigTest {

    @Test
    void sseQueueIsBoundedSoMaxThreadsCanStart() {
        ThreadPoolTaskExecutor sse = new SseExecutorConfig().sseExecutor();
        sse.afterPropertiesSet();
        try {
            assertThat(sse.getCorePoolSize()).isEqualTo(2);
            assertThat(sse.getMaxPoolSize()).isEqualTo(8);
            assertThat(sse.getQueueCapacity()).isEqualTo(16);
        } finally {
            sse.shutdown();
        }
    }

    @Test
    void deploymentsUseASeparateBoundedPool() {
        ThreadPoolTaskExecutor deploys = new SseExecutorConfig().deploymentExecutor();
        deploys.afterPropertiesSet();
        try {
            assertThat(deploys.getCorePoolSize()).isEqualTo(2);
            assertThat(deploys.getMaxPoolSize()).isEqualTo(4);
            assertThat(deploys.getQueueCapacity()).isEqualTo(8);
            assertThat(deploys.getThreadNamePrefix()).isEqualTo("deploy-");
        } finally {
            deploys.shutdown();
        }
    }
}
