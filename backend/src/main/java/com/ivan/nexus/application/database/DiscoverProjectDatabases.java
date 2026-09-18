package com.ivan.nexus.application.database;

import com.ivan.nexus.application.env.ProjectDotEnvStore;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.application.project.ContainerInspect;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.PublishedPort;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.DatabaseInstance;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.domain.database.DotEnvCredentialMerger;
import com.ivan.nexus.domain.database.EngineDetector;
import com.ivan.nexus.domain.database.NexusDatabaseExclusions;
import com.ivan.nexus.domain.database.ParsedCredentials;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.project.ProjectGrouping;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DiscoverProjectDatabases {
    private static final Duration CACHE_TTL = Duration.ofSeconds(15);

    private final ContainerInventory inventory;
    private final ProjectDotEnvStore dotEnvStore;
    private final ManifestCatalog manifests;
    private final ConcurrentHashMap<String, CachedResolutions> cache = new ConcurrentHashMap<>();

    public DiscoverProjectDatabases(
            ContainerInventory inventory,
            ProjectDotEnvStore dotEnvStore,
            ManifestCatalog manifests) {
        this.inventory = inventory;
        this.dotEnvStore = dotEnvStore;
        this.manifests = manifests;
    }

    public List<DatabaseInstance> execute(String projectId) {
        return resolutions(projectId).stream().map(InstanceResolution::instance).toList();
    }

    public InstanceResolution resolve(String projectId, String databaseId) {
        return resolutions(projectId).stream()
                .filter(item -> item.instance().id().equals(databaseId))
                .findFirst()
                .orElseThrow(() -> new DomainException(NexusErrorCode.DATABASE_NOT_FOUND, "Database not found"));
    }

    private List<InstanceResolution> resolutions(String projectId) {
        CachedResolutions cached = cache.get(projectId);
        if (cached != null && cached.fresh()) {
            return cached.items();
        }
        Map<String, String> projectEnv = dotEnvStore.read(projectId);
        String directoryName = directoryName(projectId);
        List<InstanceResolution> found = new ArrayList<>();
        for (ContainerSnapshot snapshot : inventory.listAll()) {
            if (!ProjectGrouping.belongsTo(snapshot.name(), snapshot.labels(), projectId, directoryName)) {
                continue;
            }
            if (NexusDatabaseExclusions.skip(snapshot.image(), snapshot.labels())) {
                continue;
            }
            if (EngineDetector.fromImage(snapshot.image()).isEmpty()) {
                continue;
            }
            Optional<ContainerInspect> inspect = inventory.inspect(snapshot.id());
            if (inspect.isEmpty()) {
                continue;
            }
            toResolution(projectId, inspect.get(), projectEnv).ifPresent(found::add);
        }
        List<InstanceResolution> frozen = List.copyOf(found);
        cache.put(projectId, new CachedResolutions(Instant.now().plus(CACHE_TTL), frozen));
        return frozen;
    }

    private String directoryName(String projectId) {
        try {
            LoadedManifest loaded = manifests.loadRequired(projectId);
            Path name = Path.of(loaded.manifest().project().workingDirectory()).getFileName();
            return name == null ? null : name.toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Optional<InstanceResolution> toResolution(
            String projectId,
            ContainerInspect inspect,
            Map<String, String> projectEnv) {
        if (NexusDatabaseExclusions.skip(inspect.image(), inspect.labels())) {
            return Optional.empty();
        }
        Optional<DatabaseEngine> engine = EngineDetector.fromImage(inspect.image());
        if (engine.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> merged = DotEnvCredentialMerger.merge(inspect.env(), projectEnv);
        ParsedCredentials credentials = DotEnvCredentialMerger.parseWithFallback(engine.get(), merged);
        String service = ProjectGrouping.serviceId(inspect.name(), inspect.labels());
        String databaseId = databaseId(projectId, inspect.id());
        Optional<HostPort> hostPort = resolveHost(engine.get(), inspect, merged);
        DatabaseStatus status = credentials.reachable() && hostPort.isPresent()
                ? DatabaseStatus.READY
                : DatabaseStatus.UNREACHABLE;
        DatabaseInstance instance = new DatabaseInstance(
                databaseId,
                projectId,
                inspect.id(),
                service,
                engine.get(),
                status,
                credentials.defaultDatabase());
        ResolvedTarget target = null;
        if (status == DatabaseStatus.READY && hostPort.isPresent()) {
            HostPort endpoint = hostPort.get();
            target = new ResolvedTarget(
                    endpoint.host(),
                    endpoint.port(),
                    credentials.username(),
                    credentials.password(),
                    credentials.defaultDatabase());
        }
        return Optional.of(new InstanceResolution(instance, target));
    }

    static String databaseId(String projectId, String containerId) {
        String hex = containerId == null ? "" : containerId;
        if (hex.toLowerCase(Locale.ROOT).startsWith("sha256:")) {
            hex = hex.substring("sha256:".length());
        }
        String shortId = hex.length() >= 12 ? hex.substring(0, 12) : hex;
        return projectId + ":" + shortId;
    }

    private static Optional<HostPort> resolveHost(
            DatabaseEngine engine,
            ContainerInspect inspect,
            Map<String, String> mergedEnv) {
        int defaultPort = engine.defaultPort();
        for (PublishedPort port : inspect.publishedPorts()) {
            if (port.hostPort() != null && port.privatePort() == defaultPort) {
                return Optional.of(new HostPort("127.0.0.1", port.hostPort()));
            }
        }
        DotEnvCredentialMerger.HostHint hint = DotEnvCredentialMerger.hostHint(mergedEnv);
        if (hint != null && DotEnvCredentialMerger.isRoutableHost(hint.host())) {
            int port = hint.port() != null ? hint.port() : defaultPort;
            return Optional.of(new HostPort(hint.host(), port));
        }
        for (String ip : inspect.networkIps()) {
            if (ip != null && !ip.isBlank()) {
                return Optional.of(new HostPort(ip, defaultPort));
            }
        }
        return Optional.empty();
    }

    private record HostPort(String host, int port) {}

    private record CachedResolutions(Instant expiresAt, List<InstanceResolution> items) {
        boolean fresh() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
