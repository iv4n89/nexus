package com.ivan.nexus.domain.container;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ContainerSnapshot(
    String id,
    String name,
    String image,
    String status,
    String state,
    String health,
    Instant created,
    Map<String, String> labels,
    List<PortMapping> ports,
    int restartCount,
    Instant startedAt
) {
    public record PortMapping(Integer publicPort, int privatePort) {
    }

    public ContainerSnapshot {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        ports = ports == null ? List.of() : List.copyOf(ports);
    }
}
