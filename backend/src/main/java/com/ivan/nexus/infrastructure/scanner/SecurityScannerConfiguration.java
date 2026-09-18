package com.ivan.nexus.infrastructure.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.application.security.SecurityScanner;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NexusProperties.class)
public class SecurityScannerConfiguration {

    @Bean
    TrivyReportParser trivyReportParser(ObjectMapper objectMapper) {
        return new TrivyReportParser(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "nexus.security.trivy.enabled", havingValue = "true")
    SecurityScanner trivySecurityScanner(
            ManifestCatalog manifests,
            NexusProperties properties,
            TrivyReportParser parser) {
        return new TrivySecurityScanner(manifests, properties.getSecurity().getTrivy(), parser);
    }

    @Bean
    @ConditionalOnProperty(
            name = "nexus.security.trivy.enabled",
            havingValue = "false",
            matchIfMissing = true)
    SecurityScanner noOpSecurityScanner() {
        return new NoOpSecurityScanner();
    }
}
