package com.ivan.nexus.application.alert;

import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetAlertsTest {

    @Test
    void defaultsToOpenStatusesAndReturnsDomainAlerts() {
        AlertStore alerts = mock(AlertStore.class);
        UUID id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID ruleId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Alert alert = new Alert(
                id,
                ruleId,
                "lab",
                "web",
                AlertStatus.ACTIVE,
                "Container web is exited",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                AlertType.CONTAINER_STOPPED);
        when(alerts.latest(any())).thenReturn(List.of(alert));

        List<Alert> result = new GetAlerts(alerts).execute(null);

        assertThat(result).containsExactly(new Alert(
                id,
                ruleId,
                "lab",
                "web",
                AlertStatus.ACTIVE,
                "Container web is exited",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                AlertType.CONTAINER_STOPPED));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<AlertStatus>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(alerts).latest(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED);
    }

    @Test
    void mapsNullRuleToNullRuleIdAndType() {
        AlertStore alerts = mock(AlertStore.class);
        UUID id = UUID.randomUUID();
        Alert alert = new Alert(
                id,
                null,
                "lab",
                "api",
                AlertStatus.RESOLVED,
                "resolved",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:01:00Z"),
                Instant.parse("2026-01-01T00:02:00Z"),
                null);
        when(alerts.latest(any())).thenReturn(List.of(alert));

        List<Alert> result = new GetAlerts(alerts).execute(List.of(AlertStatus.RESOLVED));

        assertThat(result).containsExactly(new Alert(
                id,
                null,
                "lab",
                "api",
                AlertStatus.RESOLVED,
                "resolved",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:01:00Z"),
                Instant.parse("2026-01-01T00:02:00Z"),
                null));
        verify(alerts).latest(List.of(AlertStatus.RESOLVED));
    }

    @Test
    void doesNotTruncateMoreThanTwoHundredMatchingAlerts() {
        AlertStore alerts = mock(AlertStore.class);
        List<Alert> expected = java.util.stream.IntStream.range(0, 201)
                .mapToObj(index -> new Alert(
                        new UUID(0, index),
                        null,
                        "lab",
                        "api",
                        AlertStatus.RESOLVED,
                        "resolved-" + index,
                        Instant.parse("2026-01-01T00:00:00Z").minusSeconds(index),
                        null,
                        Instant.parse("2026-01-01T00:01:00Z"),
                        null))
                .toList();
        when(alerts.latest(List.of(AlertStatus.RESOLVED))).thenReturn(expected);

        List<Alert> result = new GetAlerts(alerts).execute(List.of(AlertStatus.RESOLVED));

        assertThat(result).containsExactlyElementsOf(expected);
    }
}
