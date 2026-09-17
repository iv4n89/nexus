package com.ivan.nexus.infrastructure.config;

import com.ivan.nexus.application.activity.RetentionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RetentionConfiguration.class)
            .withPropertyValues(
                    "nexus.retention.activity-days=7",
                    "nexus.retention.deployment-events-days=14",
                    "nexus.retention.fingerprint-days=45");

    @Test
    void mapsNonDefaultPropertiesToApplicationPolicy() {
        contextRunner.run(context -> {
            RetentionPolicy policy = context.getBean(RetentionPolicy.class);
            assertThat(policy.activity()).isEqualTo(Duration.ofDays(7));
            assertThat(policy.deploymentEvents()).isEqualTo(Duration.ofDays(14));
            assertThat(policy.fingerprints()).isEqualTo(Duration.ofDays(45));
        });
    }

    @Test
    void suppliesUtcClockForCleanup() {
        contextRunner.run(context ->
                assertThat(context.getBean(Clock.class)).isEqualTo(Clock.systemUTC()));
    }
}
