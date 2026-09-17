package com.ivan.nexus.application.database;

import com.ivan.nexus.application.project.ContainerInspect;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.PublishedPort;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.DatabaseInstance;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.domain.database.EngineDetector;
import com.ivan.nexus.domain.database.EnvCredentialParser;
import com.ivan.nexus.domain.database.NexusDatabaseExclusions;
import com.ivan.nexus.domain.database.ParsedCredentials;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.project.ProjectGrouping;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DiscoverProjectDatabases {
    private static final Duration CACHE_TTL = Duration.ofSeconds(15);

    private final ContainerInventory inventory;
    private final ConcurrentHashMap<String, CachedResolutions> cache = new ConcurrentHashMap<>();

    public DiscoverProjectDatabases(ContainerInventory inventory) {
        this.inventory = inventory;
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
        List<InstanceResolution> found = new ArrayList<>();
        for (ContainerSnapshot snapshot : inventory.listAll()) {
            String grouped = ProjectGrouping.projectId(snapshot.name(), snapshot.labels());
            if (!projectId.equals(grouped)) {
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
            toResolution(projectId, inspect.get()).ifPresent(found::add);
        }
        List<InstanceResolution> frozen = List.copyOf(found);
        cache.put(projectId, new CachedResolutions(Instant.now().plus(CACHE_TTL), frozen));
        return frozen;
    }

    private static Optional<InstanceResolution> toResolution(String projectId, ContainerInspect inspect) {
        if (NexusDatabaseExclusions.skip(inspect.image(), inspect.labels())) {
            return Optional.empty();
        }
        Optional<DatabaseEngine> engine = EngineDetector.fromImage(inspect.image());
        if (engine.isEmpty()) {
            return Optional.empty();
        }
        ParsedCredentials credentials = EnvCredentialParser.parse(engine.get(), inspect.env());
        String service = ProjectGrouping.serviceId(inspect.name(), inspect.labels());
        String databaseId = databaseId(projectId, inspect.id());
        Optional<HostPort> hostPort = resolveHost(engine.get(), inspect);
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

    private static Optional<HostPort> resolveHost(DatabaseEngine engine, ContainerInspect inspect) {
        int defaultPort = engine.defaultPort();
        for (PublishedPort port : inspect.publishedPorts()) {
            if (port.hostPort() != null && port.privatePort() == defaultPort) {
                return Optional.of(new HostPort("127.0.0.1", port.hostPort()));
            }
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
