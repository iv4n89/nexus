package com.ivan.nexus.infrastructure.database;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SecretSanitizerTest {

    @Test
    void stripsPasswordFromJdbcStyleMessage() {
        String raw = "FATAL: password authentication failed for user \"app\" jdbc:postgresql://10.0.0.2:5432/app?password=s3cret";
        String clean = SecretSanitizer.strip("s3cret", raw);
        assertFalse(clean.contains("s3cret"));
        assertFalse(clean.contains("jdbc:postgresql"));
    }

    @Test
    void stripAllRedactsMultipleSecrets() {
        String clean = SecretSanitizer.stripAll(
                List.of("alpha-secret", "beta-secret"),
                "failed with alpha-secret and beta-secret");
        assertEquals("failed with *** and ***", clean);
        assertFalse(clean.contains("alpha-secret"));
        assertFalse(clean.contains("beta-secret"));
    }
}
