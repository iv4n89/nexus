package com.ivan.nexus.domain.manifest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HealthUrlPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:18080",
            "http://localhost:8080/health",
            "https://172.18.0.2:8080/ready",
            "http://10.0.0.12/health",
            "http://192.168.1.20:3000",
            "http://[::1]/"
    })
    void allowsLoopbackAndPrivateHealthEndpoints(String url) {
        assertThat(HealthUrlPolicy.allowed(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://169.254.169.254/",
            "http://169.254.169.254/latest/meta-data",
            "https://metadata.google.internal/",
            "file:///etc/passwd",
            "ftp://127.0.0.1/health",
            "http://8.8.8.8/health",
            "http://example.com/health",
            "http://user:pass@127.0.0.1/health",
            "http://0.0.0.0:8080/",
            "not-a-url"
    })
    void rejectsMetadataPublicAndNonHttpUrls(String url) {
        assertThat(HealthUrlPolicy.allowed(url)).isFalse();
    }
}
