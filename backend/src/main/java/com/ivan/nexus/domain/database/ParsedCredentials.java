package com.ivan.nexus.domain.database;

public record ParsedCredentials(String username, String password, String defaultDatabase, boolean reachable) {}
