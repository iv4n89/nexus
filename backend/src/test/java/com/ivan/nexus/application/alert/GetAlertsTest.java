package com.ivan.nexus.application.alert;

import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventEntity;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventJpaRepository;
import com.ivan.nexus.infrastructure.persistence.alert.AlertRuleEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetAlertsTest {

    @Test
    void defaultsToOpenStatusesAndReturnsDomainAlerts() {
        AlertEventJpaRepository events = mock(AlertEventJpaRepository.class);
        UUID id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID ruleId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        AlertEventEntity entity = new AlertEventEntity(
                id,
                new AlertRuleEntity(ruleId, null, AlertType.CONTAINER_STOPPED, Map.of(), true),
                "lab",
                "web",
                AlertStatus.ACTIVE,
                "Container web is exited",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null);
        when(events.findByStatusInOrderByOpenedAtDesc(any())).thenReturn(List.of(entity));

        List<Alert> result = new GetAlerts(events).execute(null);

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
        verify(events).findByStatusInOrderByOpenedAtDesc(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED);
    }

    @Test
    void mapsNullRuleToNullRuleIdAndType() {
        AlertEventJpaRepository events = mock(AlertEventJpaRepository.class);
        UUID id = UUID.randomUUID();
        AlertEventEntity entity = new AlertEventEntity(
                id,
                null,
                "lab",
                "api",
                AlertStatus.RESOLVED,
                "resolved",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:01:00Z"),
                Instant.parse("2026-01-01T00:02:00Z"));
        when(events.findByStatusInOrderByOpenedAtDesc(any())).thenReturn(List.of(entity));

        List<Alert> result = new GetAlerts(events).execute(List.of(AlertStatus.RESOLVED));

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
    }
}
