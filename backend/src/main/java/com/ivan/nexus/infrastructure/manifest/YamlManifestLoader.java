package com.ivan.nexus.infrastructure.manifest;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@Component
public class YamlManifestLoader {
    private final YAMLMapper yamlMapper = new YAMLMapper();

    public ProjectManifest load(Path path) {
        try (InputStream source = Files.newInputStream(path)) {
            return load(source);
        } catch (NoSuchFileException ex) {
            throw new DomainException(NexusErrorCode.MANIFEST_NOT_FOUND, "Manifest not found");
        } catch (IOException ex) {
            throw new DomainException(NexusErrorCode.MANIFEST_INVALID, "Unable to read manifest");
        }
    }

    public ProjectManifest load(InputStream source) {
        try {
            return yamlMapper.readValue(source, ProjectManifest.class);
        } catch (IOException ex) {
            throw new DomainException(NexusErrorCode.MANIFEST_INVALID, "Unable to parse manifest YAML");
        }
    }
}
