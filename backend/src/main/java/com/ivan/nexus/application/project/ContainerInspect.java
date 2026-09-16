package com.ivan.nexus.application.project;

import java.util.List;
import java.util.Map;

public record ContainerInspect(
        String id,
        String name,
        String image,
        Map<String, String> labels,
        Map<String, String> env,
        List<PublishedPort> publishedPorts,
        List<String> networkIps
) {
    public ContainerInspect {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        env = env == null ? Map.of() : Map.copyOf(env);
        publishedPorts = publishedPorts == null ? List.of() : List.copyOf(publishedPorts);
        networkIps = networkIps == null ? List.of() : List.copyOf(networkIps);
        name = name == null ? "" : name;
    }
}
