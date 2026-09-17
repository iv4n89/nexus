package com.ivan.nexus.domain.alert;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlertTest {

    @Test
    void acknowledgeAndResolveReturnUpdatedCopiesWithoutChangingPriorState() {
        Instant openedAt = Instant.parse("2026-09-17T20:00:00Z");
        Instant acknowledgedAt = Instant.parse("2026-09-17T21:00:00Z");
        Instant resolvedAt = Instant.parse("2026-09-17T22:00:00Z");
        Alert active = new Alert(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "lab",
                "web",
                AlertStatus.ACTIVE,
                "Container web is exited",
                openedAt,
                null,
                null,
                AlertType.CONTAINER_STOPPED);

        Alert acknowledged = active.acknowledge(acknowledgedAt);
        Alert resolved = acknowledged.resolve(resolvedAt);

        assertThat(active.status()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(active.acknowledgedAt()).isNull();
        assertThat(acknowledged.status()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(acknowledged.acknowledgedAt()).isEqualTo(acknowledgedAt);
        assertThat(resolved.status()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(resolved.acknowledgedAt()).isEqualTo(acknowledgedAt);
        assertThat(resolved.resolvedAt()).isEqualTo(resolvedAt);
        assertThat(resolved.openedAt()).isEqualTo(openedAt);
    }
}
