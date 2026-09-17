package com.ivan.nexus.application.activity;

import com.ivan.nexus.infrastructure.config.NexusProperties;
import com.ivan.nexus.infrastructure.persistence.activity.ActivityEventJpaRepository;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEventJpaRepository;
import com.ivan.nexus.application.log.FingerprintStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RetentionCleanupTest {

    @Test
    void cutoffSubtractsWholeDays() {
        Instant now = Instant.parse("2026-09-16T03:00:00Z");
        assertThat(RetentionCleanup.cutoff(now, 30)).isEqualTo(Instant.parse("2026-08-17T03:00:00Z"));
        assertThat(RetentionCleanup.cutoff(now, 90)).isEqualTo(Instant.parse("2026-06-18T03:00:00Z"));
    }

    @Test
    void deletesRowsOlderThanConfiguredWindows() {
        ActivityEventJpaRepository activityEvents = mock(ActivityEventJpaRepository.class);
        DeploymentEventJpaRepository deploymentEvents = mock(DeploymentEventJpaRepository.class);
        FingerprintStore fingerprints = mock(FingerprintStore.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-16T03:00:00Z"), ZoneOffset.UTC);

        new RetentionCleanup(
                activityEvents,
                deploymentEvents,
                fingerprints,
                new NexusProperties(),
                clock).execute();

        verify(activityEvents).deleteByCreatedAtBefore(Instant.parse("2026-08-17T03:00:00Z"));
        verify(deploymentEvents).deleteByCreatedAtBefore(Instant.parse("2026-08-17T03:00:00Z"));
        verify(fingerprints).deleteByLastSeenBefore(Instant.parse("2026-06-18T03:00:00Z"));
    }
}
