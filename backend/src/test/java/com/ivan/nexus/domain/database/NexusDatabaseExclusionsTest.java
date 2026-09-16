package com.ivan.nexus.domain.database;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusDatabaseExclusionsTest {

    @Test
    void skipsNexusProjectLabel() {
        assertTrue(NexusDatabaseExclusions.skip("postgres:16", Map.of("nexus.project", "nexus")));
    }

    @Test
    void skipsNexusComposeProject() {
        assertTrue(NexusDatabaseExclusions.skip("postgres:16", Map.of("com.docker.compose.project", "nexus")));
    }

    @Test
    void skipsNexusImages() {
        assertTrue(NexusDatabaseExclusions.skip("nexus-backend:latest", Map.of()));
        assertTrue(NexusDatabaseExclusions.skip("nexus-postgres:16", Map.of()));
    }

    @Test
    void keepsLabPostgres() {
        assertFalse(NexusDatabaseExclusions.skip("postgres:16-alpine", Map.of("nexus.project", "lab")));
    }
}
