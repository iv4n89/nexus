package com.ivan.nexus.domain.database;

public record DatabaseInstance(
        String id,
        String projectId,
        String containerId,
        String service,
        DatabaseEngine engine,
        DatabaseStatus status,
        String defaultDatabase
) {}
