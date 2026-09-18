package com.ivan.nexus.infrastructure.github;

import com.ivan.nexus.application.github.GitHubClient;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(NexusProperties.class)
public class GitHubConfiguration {

    @Bean
    GitHubClient gitHubClient(RestClient.Builder restClientBuilder, NexusProperties properties) {
        return new HttpGitHubClient(restClientBuilder, properties);
    }
}
