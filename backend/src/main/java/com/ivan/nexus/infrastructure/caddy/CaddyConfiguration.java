package com.ivan.nexus.infrastructure.caddy;

import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NexusProperties.class)
@ConditionalOnProperty(name = "nexus.caddy.enabled", havingValue = "true")
public class CaddyConfiguration {
}
