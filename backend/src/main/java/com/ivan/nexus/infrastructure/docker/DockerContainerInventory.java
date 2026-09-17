package com.ivan.nexus.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;
import com.ivan.nexus.application.project.ContainerInspect;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.PublishedPort;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DockerContainerInventory implements ContainerInventory {
    private static final Logger log = LoggerFactory.getLogger(DockerContainerInventory.class);

    private final DockerClient dockerClient;

    public DockerContainerInventory(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @PostConstruct
    void ping() {
        try {
            dockerClient.pingCmd().exec();
        } catch (Exception ex) {
            log.error("Docker Engine ping failed; inventory will be unavailable until the daemon is reachable", ex);
        }
    }

    @Override
    public List<ContainerSnapshot> listAll() {
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        if (containers == null || containers.isEmpty()) {
            return List.of();
        }
        List<ContainerSnapshot> snapshots = new ArrayList<>(containers.size());
        for (Container container : containers) {
            snapshots.add(mapListed(container));
        }
        return List.copyOf(snapshots);
    }

    @Override
    public Optional<ContainerSnapshot> findById(String containerId) {
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId)
                    .exec();
            return Optional.of(toSnapshot(inspect));
        } catch (NotFoundException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ContainerInspect> inspect(String containerId) {
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId)
                    .exec();
            return Optional.of(toInspect(inspect));
        } catch (NotFoundException ex) {
            return Optional.empty();
        }
    }

    private static ContainerSnapshot mapListed(Container listed) {
        Instant created = listed.getCreated() != null
                ? Instant.ofEpochSecond(listed.getCreated())
                : null;
        return new ContainerSnapshot(
                listed.getId(),
                firstName(listed.getNames()),
                listed.getImage(),
                listed.getStatus(),
                listed.getState(),
                null,
                created,
                listed.getLabels(),
                mapPorts(listed.getPorts()),
                0,
                null);
    }

    private static ContainerSnapshot toSnapshot(InspectContainerResponse inspect) {
        ContainerConfig config = inspect.getConfig();
        InspectContainerResponse.ContainerState state = inspect.getState();
        String status = state != null ? state.getStatus() : null;
        Map<String, String> labels = config != null ? config.getLabels() : Map.of();
        return new ContainerSnapshot(
                inspect.getId(),
                stripLeadingSlash(inspect.getName()),
                config != null ? config.getImage() : null,
                status,
                status,
                healthStatus(state),
                parseInstant(inspect.getCreated()),
                labels,
                List.of(),
                inspect.getRestartCount() != null ? inspect.getRestartCount() : 0,
                state != null ? parseInstant(state.getStartedAt()) : null);
    }

    private static List<ContainerSnapshot.PortMapping> mapPorts(ContainerPort[] ports) {
        if (ports == null || ports.length == 0) {
            return List.of();
        }
        List<ContainerSnapshot.PortMapping> mapped = new ArrayList<>();
        for (ContainerPort port : ports) {
            if (port == null || port.getPrivatePort() == null) {
                continue;
            }
            mapped.add(new ContainerSnapshot.PortMapping(port.getPublicPort(), port.getPrivatePort()));
        }
        return mapped;
    }

    private static ContainerInspect toInspect(InspectContainerResponse inspect) {
        ContainerConfig config = inspect.getConfig();
        Map<String, String> labels = config != null && config.getLabels() != null ? config.getLabels() : Map.of();
        return new ContainerInspect(
                inspect.getId(),
                stripLeadingSlash(inspect.getName()),
                config != null ? config.getImage() : null,
                labels,
                parseEnv(config != null ? config.getEnv() : null),
                mapPublishedPorts(inspect.getNetworkSettings()),
                mapNetworkIps(inspect.getNetworkSettings()));
    }

    private static Map<String, String> parseEnv(String[] env) {
        if (env == null || env.length == 0) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String entry : env) {
            if (entry == null) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            values.put(entry.substring(0, eq), entry.substring(eq + 1));
        }
        return values;
    }

    private static List<PublishedPort> mapPublishedPorts(NetworkSettings settings) {
        if (settings == null || settings.getPorts() == null || settings.getPorts().getBindings() == null) {
            return List.of();
        }
        List<PublishedPort> mapped = new ArrayList<>();
        for (Map.Entry<ExposedPort, Ports.Binding[]> entry : settings.getPorts().getBindings().entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            int privatePort = entry.getKey().getPort();
            Ports.Binding[] bindings = entry.getValue();
            if (bindings == null || bindings.length == 0) {
                mapped.add(new PublishedPort(privatePort, null, null));
                continue;
            }
            for (Ports.Binding binding : bindings) {
                if (binding == null) {
                    mapped.add(new PublishedPort(privatePort, null, null));
                    continue;
                }
                mapped.add(new PublishedPort(privatePort, parseHostPort(binding.getHostPortSpec()), binding.getHostIp()));
            }
        }
        return mapped;
    }

    private static Integer parseHostPort(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String first = spec.contains("-") ? spec.substring(0, spec.indexOf('-')) : spec;
        try {
            return Integer.valueOf(first);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> mapNetworkIps(NetworkSettings settings) {
        if (settings == null || settings.getNetworks() == null) {
            return List.of();
        }
        List<String> ips = new ArrayList<>();
        for (ContainerNetwork network : settings.getNetworks().values()) {
            if (network == null) {
                continue;
            }
            String ip = network.getIpAddress();
            if (ip != null && !ip.isBlank()) {
                ips.add(ip);
            }
        }
        return ips;
    }

    private static String firstName(String[] names) {
        if (names == null || names.length == 0) {
            return "";
        }
        return stripLeadingSlash(names[0]);
    }

    private static String stripLeadingSlash(String name) {
        if (name != null && name.startsWith("/")) {
            return name.substring(1);
        }
        return name == null ? "" : name;
    }

    private static String healthStatus(InspectContainerResponse.ContainerState state) {
        if (state == null || state.getHealth() == null) {
            return null;
        }
        return state.getHealth().getStatus();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank() || value.startsWith("0001-01-01")) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException ex) {
                log.warn("Unable to parse Docker timestamp '{}'", value);
                return null;
            }
        }
    }
}
