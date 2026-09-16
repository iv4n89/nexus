package com.ivan.nexus.domain.database;

public record ResolvedTarget(
        String host,
        int port,
        String username,
        String password,
        String defaultDatabase
) {}
