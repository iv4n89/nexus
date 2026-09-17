package com.ivan.nexus.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(NexusProperties.class)
public class DockerConfiguration {
    private static final Logger log = LoggerFactory.getLogger(DockerConfiguration.class);
    static final Duration LIST_RESPONSE_TIMEOUT = Duration.ofSeconds(45);
    static final Duration FOLLOW_RESPONSE_TIMEOUT = Duration.ofHours(6);

    @Bean
    @org.springframework.context.annotation.Primary
    DockerClient dockerClient(NexusProperties properties) {
        return buildClient(properties, LIST_RESPONSE_TIMEOUT);
    }

    @Bean
    @org.springframework.beans.factory.annotation.Qualifier("dockerFollowClient")
    DockerClient dockerFollowClient(NexusProperties properties) {
        return buildClient(properties, FOLLOW_RESPONSE_TIMEOUT);
    }

    private static DockerClient buildClient(NexusProperties properties, Duration responseTimeout) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(properties.getDocker().getHost())
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(responseTimeout)
                .build();
        log.debug("Docker client configured for host {} timeout {}", properties.getDocker().getHost(), responseTimeout);
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
