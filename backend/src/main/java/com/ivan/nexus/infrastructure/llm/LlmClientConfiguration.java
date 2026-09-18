package com.ivan.nexus.infrastructure.llm;

import com.ivan.nexus.application.security.LlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmClientConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "nexus.security.llm.enabled",
            havingValue = "false",
            matchIfMissing = true)
    @ConditionalOnMissingBean(LlmClient.class)
    LlmClient noOpLlmClient() {
        return new NoOpLlmClient();
    }

    /**
     * When LLM is enabled but no real provider bean is registered, fall back to NoOp
     * so the app still starts; real providers should register their own {@link LlmClient}.
     */
    @Bean
    @ConditionalOnProperty(name = "nexus.security.llm.enabled", havingValue = "true")
    @ConditionalOnMissingBean(LlmClient.class)
    LlmClient enabledFallbackLlmClient() {
        return new NoOpLlmClient();
    }
}
