package com.ivan.nexus.application.env;

import com.ivan.nexus.domain.env.DotEnvParser;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ListProjectEnv {
    private final ProjectDotEnvStore dotEnvStore;

    public ListProjectEnv(ProjectDotEnvStore dotEnvStore) {
        this.dotEnvStore = dotEnvStore;
    }

    public List<ProjectEnvVarView> execute(String projectId) {
        Instant now = Instant.now();
        return dotEnvStore.read(projectId).entrySet().stream()
                .map(entry -> toView(projectId, entry.getKey(), entry.getValue(), now))
                .toList();
    }

    static ProjectEnvVarView toView(String projectId, String name, String value, Instant at) {
        boolean secret = DotEnvParser.looksSecret(name);
        return new ProjectEnvVarView(
                stableId(projectId, name),
                projectId,
                name,
                secret,
                secret ? null : value,
                at,
                at);
    }

    static UUID stableId(String projectId, String name) {
        return UUID.nameUUIDFromBytes((projectId + ":" + name).getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, String> readMutable(ProjectDotEnvStore store, String projectId) {
        return new java.util.LinkedHashMap<>(store.read(projectId));
    }
}
