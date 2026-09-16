package com.ivan.nexus.infrastructure.manifest;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class YamlManifestLoader {
    private final YAMLMapper yamlMapper = new YAMLMapper();

    public ProjectManifest load(InputStream source) {
        try {
            return yamlMapper.readValue(source, ProjectManifest.class);
        } catch (IOException ex) {
            throw new DomainException(NexusErrorCode.MANIFEST_INVALID, "Unable to parse manifest YAML");
        }
    }
}
