package com.ivan.nexus.infrastructure.manifest;

import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class ManifestValidatorFactory {

    @Bean(name = "manifestAllowedRoot")
    @Qualifier("manifestAllowedRoot")
    public Path manifestAllowedRoot(NexusProperties properties) {
        return Path.of(properties.getManifest().getAllowedRoot()).toAbsolutePath().normalize();
    }
}
