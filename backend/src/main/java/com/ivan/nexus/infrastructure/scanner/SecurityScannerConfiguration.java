package com.ivan.nexus.infrastructure.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.application.security.SecurityScanner;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties(NexusProperties.class)
public class SecurityScannerConfiguration {

    @Bean
    TrivyReportParser trivyReportParser(ObjectMapper objectMapper) {
        return new TrivyReportParser(objectMapper);
    }

    @Bean
    NpmAuditReportParser npmAuditReportParser(ObjectMapper objectMapper) {
        return new NpmAuditReportParser(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "nexus.security.trivy.enabled", havingValue = "true")
    TrivySecurityScanner trivySecurityScanner(
            ManifestCatalog manifests,
            NexusProperties properties,
            TrivyReportParser parser) {
        return new TrivySecurityScanner(manifests, properties.getSecurity().getTrivy(), parser);
    }

    @Bean
    @ConditionalOnProperty(name = "nexus.security.npm-audit.enabled", havingValue = "true")
    OsvNpmSecurityScanner osvNpmSecurityScanner(
            ManifestCatalog manifests,
            NexusProperties properties,
            NpmAuditReportParser parser) {
        return new OsvNpmSecurityScanner(manifests, properties.getSecurity().getNpmAudit(), parser);
    }

    @Bean
    @Primary
    SecurityScanner securityScanner(
            ObjectProvider<TrivySecurityScanner> trivy,
            ObjectProvider<OsvNpmSecurityScanner> npm) {
        List<SecurityScanner> delegates = new ArrayList<>();
        trivy.ifAvailable(delegates::add);
        npm.ifAvailable(delegates::add);
        if (delegates.isEmpty()) {
            return new NoOpSecurityScanner();
        }
        if (delegates.size() == 1) {
            return delegates.getFirst();
        }
        return new CompositeSecurityScanner(delegates);
    }
}
