package com.ivan.nexus.infrastructure.docker;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DockerConfigurationTest {

    @Test
    void followClientWaitsLongerThanSseHeartbeat() {
        assertThat(DockerConfiguration.FOLLOW_RESPONSE_TIMEOUT).isGreaterThanOrEqualTo(Duration.ofMinutes(1));
        assertThat(DockerConfiguration.LIST_RESPONSE_TIMEOUT).isEqualTo(Duration.ofSeconds(45));
        assertThat(DockerConfiguration.FOLLOW_RESPONSE_TIMEOUT)
                .isGreaterThan(DockerConfiguration.LIST_RESPONSE_TIMEOUT);
    }
}
