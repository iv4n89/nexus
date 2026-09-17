package com.ivan.nexus.infrastructure.manifest;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.domain.manifest.ManifestValidator;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class YamlManifestCatalog implements ManifestCatalog {
    private static final String MANIFEST_FILE = "nexus.yml";

    private final Path allowedRoot;
    private final YAMLMapper yamlMapper = new YAMLMapper();

    public YamlManifestCatalog(@Qualifier("manifestAllowedRoot") Path allowedRoot) {
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
    }

    @Override
    public Set<String> discoverProjectIds() {
        if (!Files.isDirectory(allowedRoot)) {
            return Set.of();
        }
        try (var projects = Files.list(allowedRoot)) {
            return projects
                    .map(project -> project.getFileName().toString())
                    .filter(projectId -> usableManifestPath(projectId).isPresent())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException | SecurityException ignored) {
            return Set.of();
        }
    }

    @Override
    public boolean exists(String projectId) {
        return usableManifestPath(projectId).isPresent();
    }

    @Override
    public LoadedManifest loadRequired(String projectId) {
        Path manifestPath = usableManifestPath(projectId).orElseThrow(YamlManifestCatalog::notFound);
        ProjectManifest manifest = parse(manifestPath);
        if (manifest == null || manifest.project() == null || !projectId.equals(manifest.project().id())) {
            throw new DomainException(NexusErrorCode.MANIFEST_INVALID, "project.id does not match");
        }
        ManifestValidator.validate(manifest, allowedRoot);
        return new LoadedManifest(manifest, manifestPath);
    }

    private Path manifestPath(String projectId) {
        return allowedRoot.resolve(projectId).resolve(MANIFEST_FILE).toAbsolutePath().normalize();
    }

    private Optional<Path> usableManifestPath(String projectId) {
        if (!isSafePathSegment(projectId)) {
            return Optional.empty();
        }

        Path projectPath = allowedRoot.resolve(projectId).toAbsolutePath().normalize();
        Path manifestPath = projectPath.resolve(MANIFEST_FILE).toAbsolutePath().normalize();
        if (!projectPath.startsWith(allowedRoot) || !manifestPath.startsWith(allowedRoot)) {
            return Optional.empty();
        }

        try {
            Path realRoot = allowedRoot.toRealPath();
            Path realProject = projectPath.toRealPath();
            Path realManifest = manifestPath.toRealPath();
            if (!Files.isDirectory(realRoot)
                    || !realProject.startsWith(realRoot)
                    || !Files.isDirectory(realProject)
                    || !realManifest.startsWith(realRoot)
                    || !Files.isRegularFile(realManifest)) {
                return Optional.empty();
            }
            return Optional.of(manifestPath);
        } catch (IOException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private static boolean isSafePathSegment(String projectId) {
        if (projectId == null
                || projectId.isBlank()
                || ".".equals(projectId)
                || "..".equals(projectId)
                || projectId.contains("/")
                || projectId.contains("\\")) {
            return false;
        }
        try {
            Path path = Path.of(projectId);
            return !path.isAbsolute() && path.getNameCount() == 1;
        } catch (InvalidPathException ignored) {
            return false;
        }
    }

    private static DomainException notFound() {
        return new DomainException(NexusErrorCode.MANIFEST_NOT_FOUND, "Manifest not found");
    }

    private ProjectManifest parse(Path path) {
        try (InputStream source = open(path)) {
            return yamlMapper.readValue(source, ProjectManifest.class);
        } catch (IOException ex) {
            throw new DomainException(NexusErrorCode.MANIFEST_INVALID, "Unable to parse manifest YAML");
        }
    }

    private InputStream open(Path path) {
        try {
            return Files.newInputStream(path);
        } catch (NoSuchFileException ex) {
            throw new DomainException(NexusErrorCode.MANIFEST_NOT_FOUND, "Manifest not found");
        } catch (IOException ex) {
            throw new DomainException(NexusErrorCode.MANIFEST_INVALID, "Unable to read manifest");
        }
    }
}
