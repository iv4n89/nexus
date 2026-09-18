package com.ivan.nexus.infrastructure.secrets;

import com.ivan.nexus.application.secrets.SecretStore;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NexusProperties.class)
public class SecretsConfiguration {

    @Bean
    SecretStore secretStore(NexusProperties properties) {
        return new AesGcmSecretStore(properties.getSecrets().getKey());
    }
}
