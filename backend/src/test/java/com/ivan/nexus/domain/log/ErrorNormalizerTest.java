package com.ivan.nexus.domain.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorNormalizerTest {

    @Test
    void sameFingerprintForDifferentIps() {
        var a = ErrorNormalizer.normalize("Connection to 10.0.0.31 failed at 12:42");
        var b = ErrorNormalizer.normalize("Connection to 10.0.0.44 failed at 12:43");
        assertEquals(a.orElseThrow().fingerprint(), b.orElseThrow().fingerprint());
    }

    @Test
    void nonMatchingLineIsEmpty() {
        assertThat(ErrorNormalizer.normalize("INFO started successfully")).isEmpty();
        assertThat(ErrorNormalizer.normalize("request completed")).isEmpty();
    }

    @Test
    void uuidAndIso8601TimestampsCollapse() {
        var a = ErrorNormalizer.normalize(
                "ERROR user 550e8400-e29b-41d4-a716-446655440000 at 2026-01-15T12:42:00Z");
        var b = ErrorNormalizer.normalize(
                "ERROR user 123e4567-e89b-12d3-a456-426614174000 at 2026-09-16T08:01:02.123Z");
        assertThat(a).isPresent();
        assertThat(b).isPresent();
        assertThat(a.orElseThrow().fingerprint()).isEqualTo(b.orElseThrow().fingerprint());
    }

    @Test
    void digitRunsForPortsAndIdsCollapse() {
        var a = ErrorNormalizer.normalize("timeout connecting to port 8080 id 99887766");
        var b = ErrorNormalizer.normalize("timeout connecting to port 9090 id 11223344");
        assertThat(a.orElseThrow().fingerprint()).isEqualTo(b.orElseThrow().fingerprint());
    }

    @Test
    void fingerprintIsSha256HexOfNormalizedUtf8() {
        var result = ErrorNormalizer.normalize("ERROR boom").orElseThrow();
        assertThat(result.fingerprint()).matches("[0-9a-f]{64}");
        assertThat(result.normalized()).isNotBlank();
        assertThat(result.sampleMessage()).isEqualTo("ERROR boom");
    }
}
