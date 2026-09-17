package com.ivan.nexus.infrastructure.config;

import com.ivan.nexus.application.activity.RetentionPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(NexusProperties.class)
public class RetentionConfiguration {

    @Bean
    RetentionPolicy retentionPolicy(NexusProperties properties) {
        NexusProperties.Retention retention = properties.getRetention();
        return new RetentionPolicy(
                Duration.ofDays(retention.getActivityDays()),
                Duration.ofDays(retention.getDeploymentEventsDays()),
                Duration.ofDays(retention.getFingerprintDays()));
    }

    @Bean
    Clock retentionClock() {
        return Clock.systemUTC();
    }
}
