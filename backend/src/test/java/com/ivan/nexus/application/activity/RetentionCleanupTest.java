package com.ivan.nexus.application.activity;

import com.ivan.nexus.application.deployment.DeploymentEventStore;
import com.ivan.nexus.application.log.FingerprintStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RetentionCleanupTest {

    @Test
    void cutoffSubtractsWholeDays() {
        Instant now = Instant.parse("2026-09-16T03:00:00Z");
        assertThat(RetentionCleanup.cutoff(now, Duration.ofDays(30)))
                .isEqualTo(Instant.parse("2026-08-17T03:00:00Z"));
        assertThat(RetentionCleanup.cutoff(now, Duration.ofDays(90)))
                .isEqualTo(Instant.parse("2026-06-18T03:00:00Z"));
    }

    @Test
    void deletesRowsOlderThanConfiguredWindows() {
        ActivityStore activityEvents = mock(ActivityStore.class);
        DeploymentEventStore deploymentEvents = mock(DeploymentEventStore.class);
        FingerprintStore fingerprints = mock(FingerprintStore.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-16T03:00:00Z"), ZoneOffset.UTC);
        RetentionPolicy policy = new RetentionPolicy(
                Duration.ofDays(7),
                Duration.ofDays(14),
                Duration.ofDays(45));

        new RetentionCleanup(
                activityEvents,
                deploymentEvents,
                fingerprints,
                policy,
                clock).execute();

        verify(activityEvents).deleteCreatedBefore(Instant.parse("2026-09-09T03:00:00Z"));
        verify(deploymentEvents).deleteCreatedBefore(Instant.parse("2026-09-02T03:00:00Z"));
        verify(fingerprints).deleteByLastSeenBefore(Instant.parse("2026-08-02T03:00:00Z"));
    }
}
