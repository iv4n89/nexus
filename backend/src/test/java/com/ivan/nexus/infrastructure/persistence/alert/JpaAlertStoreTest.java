package com.ivan.nexus.infrastructure.persistence.alert;

import com.ivan.nexus.application.alert.AlertStore;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertKey;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaAlertStoreTest {
    private AlertEventJpaRepository events;
    private AlertRuleJpaRepository rules;
    private AlertStore store;

    @BeforeEach
    void setUp() {
        events = mock(AlertEventJpaRepository.class);
        rules = mock(AlertRuleJpaRepository.class);
        store = new JpaAlertStore(events, rules);
    }

    @Test
    void latestUsesBoundedNewestFirstQueryAndMapsRuleWhileManaged() {
        Alert alert = alert(AlertStatus.ACTIVE, null, null);
        when(events.findByStatusInOrderByOpenedAtDesc(any(), any()))
                .thenReturn(List.of(entity(alert)));

        assertThat(store.latest(List.of(AlertStatus.ACTIVE), 37)).containsExactly(alert);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(events).findByStatusInOrderByOpenedAtDesc(any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(37);
    }

    @Test
    void openLoadsRuleAndMapsDomainAlertToEntity() {
        Alert alert = alert(AlertStatus.ACTIVE, null, null);
        AlertRuleEntity rule = new AlertRuleEntity(
                alert.ruleId(), null, alert.type(), Map.of(), true);
        when(rules.getReferenceById(alert.ruleId())).thenReturn(rule);

        store.open(alert);

        var order = inOrder(rules, events);
        order.verify(rules).getReferenceById(alert.ruleId());
        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        order.verify(events).save(captor.capture());
        AlertEventEntity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(alert.id());
        assertThat(saved.getRule()).isSameAs(rule);
        assertThat(saved.getStatus()).isEqualTo(alert.status());
        assertThat(saved.getOpenedAt()).isEqualTo(alert.openedAt());
    }

    @Test
    void findByIdAndFindOpenMapManagedEntitiesToDomainAlerts() {
        Alert alert = alert(AlertStatus.ACTIVE, null, null);
        AlertEventEntity entity = entity(alert);
        AlertKey key = new AlertKey(alert.type(), alert.projectId(), alert.serviceId());
        when(events.findById(alert.id())).thenReturn(Optional.of(entity));
        when(events.findOpenByTypeAndProjectAndService(
                alert.type(), alert.projectId(), alert.serviceId()))
                .thenReturn(Optional.of(entity));

        assertThat(store.findById(alert.id())).contains(alert);
        assertThat(store.findOpen(key)).contains(alert);
    }

    @Test
    void acknowledgeAndResolveLoadBeforeMutatingAndReturnPersistedDomainAlert() {
        Instant acknowledgedAt = Instant.parse("2026-09-17T21:00:00Z");
        Alert active = alert(AlertStatus.ACTIVE, null, null);
        AlertEventEntity activeEntity = entity(active);
        when(events.findById(active.id())).thenReturn(Optional.of(activeEntity));

        Alert acknowledged = store.acknowledge(active.id(), acknowledgedAt).orElseThrow();

        var acknowledgeOrder = inOrder(events);
        acknowledgeOrder.verify(events).findById(active.id());
        acknowledgeOrder.verify(events).save(activeEntity);
        assertThat(acknowledged.status()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(acknowledged.acknowledgedAt()).isEqualTo(acknowledgedAt);

        Instant resolvedAt = Instant.parse("2026-09-17T22:00:00Z");
        AlertKey key = new AlertKey(AlertType.DISK, null, null);
        when(events.findOpenByTypeAndProjectAndService(AlertType.DISK, null, null))
                .thenReturn(Optional.of(activeEntity));

        Alert resolved = store.resolve(key, resolvedAt).orElseThrow();

        verify(events, times(2)).save(activeEntity);
        assertThat(resolved.status()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(resolved.acknowledgedAt()).isEqualTo(acknowledgedAt);
        assertThat(resolved.resolvedAt()).isEqualTo(resolvedAt);
    }

    private static Alert alert(AlertStatus status, Instant acknowledgedAt, Instant resolvedAt) {
        return new Alert(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                null,
                null,
                status,
                "Host disk is 90%",
                Instant.parse("2026-09-17T20:00:00Z"),
                acknowledgedAt,
                resolvedAt,
                AlertType.DISK);
    }

    private static AlertEventEntity entity(Alert alert) {
        return new AlertEventEntity(
                alert.id(),
                new AlertRuleEntity(alert.ruleId(), null, alert.type(), Map.of(), true),
                alert.projectId(),
                alert.serviceId(),
                alert.status(),
                alert.message(),
                alert.openedAt(),
                alert.acknowledgedAt(),
                alert.resolvedAt());
    }
}
