package com.ivan.nexus.domain.database;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusDatabaseExclusionsTest {

    @Test
    void keepsPostgresEvenWhenComposeProjectIsNexus() {
        assertFalse(NexusDatabaseExclusions.skip("postgres:16", Map.of("nexus.project", "nexus")));
        assertFalse(NexusDatabaseExclusions.skip(
                "postgres:16-alpine", Map.of("com.docker.compose.project", "nexus")));
    }

    @Test
    void skipsNexusBackendImage() {
        assertTrue(NexusDatabaseExclusions.skip("nexus-backend:latest", Map.of()));
    }

    @Test
    void keepsLabPostgres() {
        assertFalse(NexusDatabaseExclusions.skip("postgres:16-alpine", Map.of("nexus.project", "lab")));
    }
}
