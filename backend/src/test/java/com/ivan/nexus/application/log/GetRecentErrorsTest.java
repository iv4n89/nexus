package com.ivan.nexus.application.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetRecentErrorsTest {

    @Mock
    FingerprintStore fingerprints;

    @Test
    void dropsStoredStackFramesAndInfoNoise() {
        Instant now = Instant.parse("2026-09-16T13:00:00Z");
        given(fingerprints.findTop10ByProjectIdAndLastSeenAfterOrderByLastSeenDesc(eq("nexus"), any()))
                .willReturn(List.of(
                        entity("backend", "        at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:125)", now),
                        entity("backend", "ERROR boom", now)));

        List<GetRecentErrors.RecentError> errors = new GetRecentErrors(fingerprints).execute("nexus");

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().sampleMessage()).isEqualTo("ERROR boom");
    }

    private static StoredErrorFingerprint entity(String service, String sample, Instant seen) {
        return new StoredErrorFingerprint(
                UUID.randomUUID(), "nexus", service, "hash", seen, seen, 3L, sample);
    }
}
