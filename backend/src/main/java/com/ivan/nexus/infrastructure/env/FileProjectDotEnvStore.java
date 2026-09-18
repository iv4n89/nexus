package com.ivan.nexus.infrastructure.env;

import com.ivan.nexus.application.env.ProjectDotEnvStore;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.env.DotEnvParser;
import com.ivan.nexus.domain.project.ProjectGrouping;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves {@code .env} as:
 * <ol>
 *   <li>manifest {@code workingDirectory}/.env</li>
 *   <li>{@code com.docker.compose.project.working_dir}/.env (e.g. {@code /opt/nexus/.env})</li>
 *   <li>{@code {allowedRoot}/{projectId}/.env}</li>
 * </ol>
 * Paths must stay under configured allowed roots (manifest root + extras such as {@code /opt}).
 */
@Component
public class FileProjectDotEnvStore implements ProjectDotEnvStore {
    private static final Logger log = LoggerFactory.getLogger(FileProjectDotEnvStore.class);

    private final ManifestCatalog manifests;
    private final ContainerInventory inventory;
    private final List<Path> allowedRoots;

    @Autowired
    public FileProjectDotEnvStore(
            ManifestCatalog manifests,
            ContainerInventory inventory,
            @Qualifier("manifestAllowedRoot") Path allowedRoot,
            @Value("${nexus.dotenv.extra-roots:/opt}") List<String> extraRoots) {
        this.manifests = manifests;
        this.inventory = inventory;
        this.allowedRoots = buildAllowedRoots(allowedRoot, extraRoots);
    }

    @Override
    public Map<String, String> read(String projectId) {
        try {
            Path envFile = resolveEnvPath(projectId);
            if (envFile == null || !Files.isRegularFile(envFile)) {
                return new LinkedHashMap<>();
            }
            String content = Files.readString(envFile, StandardCharsets.UTF_8);
            return new LinkedHashMap<>(DotEnvParser.parse(content));
        } catch (Exception ex) {
            log.warn("Unable to read .env for project {}: {}", projectId, ex.toString());
            return new LinkedHashMap<>();
        }
    }

    @Override
    public void write(String projectId, Map<String, String> values) {
        Path envFile = resolveEnvPathRequired(projectId);
        try {
            Files.createDirectories(envFile.getParent());
            Path temp = envFile.resolveSibling(".env.nexus.tmp");
            Files.writeString(temp, DotEnvParser.format(values), StandardCharsets.UTF_8);
            trySetOwnerOnly(temp);
            Files.move(temp, envFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            trySetOwnerOnly(envFile);
        } catch (IOException ex) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "Unable to write .env");
        }
    }

    Path resolveEnvPathRequired(String projectId) {
        Path envFile = resolveEnvPath(projectId);
        if (envFile == null) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "Invalid .env path");
        }
        return envFile;
    }

    /**
     * @return absolute {@code .env} path, or {@code null} when no safe path can be resolved
     */
    Path resolveEnvPath(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        Path projectDir = resolveProjectDir(projectId);
        if (projectDir == null) {
            return null;
        }
        Path envFile = projectDir.resolve(".env").toAbsolutePath().normalize();
        if (!isUnderAllowedRoot(envFile)) {
            return null;
        }
        return envFile;
    }

    private Path resolveProjectDir(String projectId) {
        Path fromManifest = fromManifest(projectId);
        if (fromManifest != null) {
            return fromManifest;
        }
        Path fromCompose = fromComposeWorkingDir(projectId);
        if (fromCompose != null) {
            return fromCompose;
        }
        Path fallback = allowedRoots.getFirst().resolve(projectId).toAbsolutePath().normalize();
        return isUnderAllowedRoot(fallback) ? fallback : null;
    }

    private Path fromManifest(String projectId) {
        try {
            LoadedManifest loaded = manifests.loadRequired(projectId);
            String working = loaded.manifest().project().workingDirectory();
            if (working == null || working.isBlank()) {
                return null;
            }
            Path projectDir = Path.of(working).toAbsolutePath().normalize();
            return isUnderAllowedRoot(projectDir) ? projectDir : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Path fromComposeWorkingDir(String projectId) {
        try {
            for (ContainerSnapshot snapshot : inventory.listAll()) {
                if (!ProjectGrouping.belongsTo(snapshot.name(), snapshot.labels(), projectId, null)) {
                    continue;
                }
                String working = ProjectGrouping.composeWorkingDir(snapshot.labels());
                if (working == null) {
                    continue;
                }
                Path projectDir = Path.of(working).toAbsolutePath().normalize();
                if (isUnderAllowedRoot(projectDir)) {
                    return projectDir;
                }
            }
        } catch (RuntimeException ex) {
            log.debug("compose working_dir lookup failed for {}: {}", projectId, ex.toString());
        }
        return null;
    }

    private boolean isUnderAllowedRoot(Path path) {
        if (path == null) {
            return false;
        }
        Path normalized = path.toAbsolutePath().normalize();
        for (Path root : allowedRoots) {
            if (normalized.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> buildAllowedRoots(Path allowedRoot, List<String> extraRoots) {
        List<Path> roots = new ArrayList<>();
        roots.add(allowedRoot.toAbsolutePath().normalize());
        if (extraRoots != null) {
            for (String raw : extraRoots) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                Path extra = Path.of(raw.trim()).toAbsolutePath().normalize();
                if (!roots.contains(extra)) {
                    roots.add(extra);
                }
            }
        }
        return List.copyOf(roots);
    }

    private static void trySetOwnerOnly(Path path) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // non-POSIX FS
        }
    }
}
