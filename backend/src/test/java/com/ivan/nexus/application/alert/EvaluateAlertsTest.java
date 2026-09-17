package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.deployment.HealthChecker;
import com.ivan.nexus.application.metrics.ContainerStatsProvider;
import com.ivan.nexus.application.metrics.GetSystemMetrics;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.metrics.ContainerMetrics;
import com.ivan.nexus.domain.metrics.SystemMetrics;
import com.ivan.nexus.infrastructure.manifest.YamlManifestLoader;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventEntity;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventJpaRepository;
import com.ivan.nexus.infrastructure.persistence.alert.AlertRuleEntity;
import com.ivan.nexus.infrastructure.persistence.alert.AlertRuleJpaRepository;
import com.ivan.nexus.application.log.FingerprintStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EvaluateAlertsTest {

    @Mock
    ContainerStatsProvider statsProvider;
    @Mock
    GetSystemMetrics getSystemMetrics;
    @Mock
    FingerprintStore fingerprints;
    @Mock
    HealthChecker healthChecker;
    @Mock
    AlertRuleJpaRepository rules;
    @Mock
    AlertEventJpaRepository events;
    @Mock
    RecordActivity recordActivity;

    private final List<ContainerSnapshot> inventory = new ArrayList<>();
    private EvaluateAlerts evaluateAlerts;

    @BeforeEach
    void setUp() {
        given(getSystemMetrics.execute()).willReturn(new SystemMetrics(0, 0, 1, 10, 100, 0, 0));
        given(fingerprints.findAll()).willReturn(List.of());
        given(rules.findByEnabledTrue()).willReturn(List.of(rule(AlertType.CONTAINER_STOPPED)));
        DiscoverProjects discoverProjects = new DiscoverProjects(inventory(), "/tmp/nexus-no-manifests");
        evaluateAlerts = new EvaluateAlerts(
                discoverProjects,
                new YamlManifestLoader(),
                rules,
                new PersistAlertEvaluation(events, recordActivity),
                new AlertFactCollector(
                        discoverProjects, statsProvider, getSystemMetrics, fingerprints, healthChecker),
                new ContainerLifecycleNotifier(recordActivity),
                "/tmp/nexus-no-manifests");
    }

    @Test
    void persistsActiveAlertWhenContainerStops() {
        given(events.findOpenByTypeAndProjectAndService(any(), any(), any())).willReturn(Optional.empty());
        given(events.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        inventory.add(snapshot("running"));
        evaluateAlerts.execute();

        inventory.clear();
        inventory.add(snapshot("exited"));
        evaluateAlerts.execute();

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(events).save(captor.capture());
        AlertEventEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(saved.getProjectId()).isEqualTo("lab");
        assertThat(saved.getServiceId()).isEqualTo("web");
        assertThat(saved.getRule().getType()).isEqualTo(AlertType.CONTAINER_STOPPED);
        assertThat(saved.getResolvedAt()).isNull();
        verify(recordActivity).execute(
                eq(ActivityType.ALERT_CREATED), eq("lab"), eq("web"), eq("alert created"), any());
        verify(recordActivity).execute(
                eq(ActivityType.CONTAINER_STOPPED), eq("lab"), eq("web"), eq("stopped"), any());
    }

    @Test
    void doesNotDuplicateOpenAlertWhenConditionStillHolds() {
        List<AlertEventEntity> saved = new ArrayList<>();
        given(events.findOpenByTypeAndProjectAndService(any(), any(), any()))
                .willAnswer(invocation -> openOfType(saved, invocation.getArgument(0)));
        given(events.save(any())).willAnswer(invocation -> {
            AlertEventEntity entity = invocation.getArgument(0);
            saved.removeIf(existing -> existing.getId().equals(entity.getId()));
            saved.add(entity);
            return entity;
        });

        inventory.add(snapshot("running"));
        evaluateAlerts.execute();
        inventory.clear();
        inventory.add(snapshot("exited"));
        evaluateAlerts.execute();
        evaluateAlerts.execute();

        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getStatus()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(saved.getFirst().getResolvedAt()).isNull();
    }

    @Test
    void resolvesOpenAlertWhenConditionClears() {
        List<AlertEventEntity> saved = new ArrayList<>();
        given(events.findOpenByTypeAndProjectAndService(any(), any(), any()))
                .willAnswer(invocation -> openOfType(saved, invocation.getArgument(0)));
        given(events.save(any())).willAnswer(invocation -> {
            AlertEventEntity entity = invocation.getArgument(0);
            saved.removeIf(existing -> existing.getId().equals(entity.getId()));
            saved.add(entity);
            return entity;
        });

        inventory.add(snapshot("running"));
        evaluateAlerts.execute();
        inventory.clear();
        inventory.add(snapshot("exited"));
        evaluateAlerts.execute();
        inventory.clear();
        inventory.add(snapshot("running"));
        evaluateAlerts.execute();

        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(saved.getFirst().getResolvedAt()).isNotNull();
        verify(recordActivity).execute(
                eq(ActivityType.ALERT_RESOLVED), eq("lab"), eq("web"), eq("alert resolved"), any());
        verify(recordActivity).execute(
                eq(ActivityType.CONTAINER_STARTED), eq("lab"), eq("web"), eq("started"), any());
    }

    @Test
    void recordsRestartWhenRestartCountIncreases() {
        inventory.add(snapshot("running", 0));
        evaluateAlerts.execute();
        inventory.clear();
        inventory.add(snapshot("running", 2));
        evaluateAlerts.execute();

        verify(recordActivity).execute(
                eq(ActivityType.CONTAINER_RESTARTED), eq("lab"), eq("web"), eq("restarted"), any());
    }

    @Test
    void persistsHighMemoryWhenUsageExceedsDefaultThreshold() {
        given(rules.findByEnabledTrue()).willReturn(List.of(rule(AlertType.HIGH_MEMORY)));
        given(statsProvider.stats("web-id")).willReturn(new ContainerMetrics("web-id", 0, 95, 100, 0, 0));
        given(events.findOpenByTypeAndProjectAndService(any(), any(), any())).willReturn(Optional.empty());
        given(events.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        inventory.add(snapshot("running"));
        evaluateAlerts.execute();

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getRule().getType()).isEqualTo(AlertType.HIGH_MEMORY);
        assertThat(captor.getValue().getStatus()).isEqualTo(AlertStatus.ACTIVE);
    }

    @Test
    void persistsDiskAlertWhenUsageExceedsDefaultThreshold() {
        given(rules.findByEnabledTrue()).willReturn(List.of(rule(AlertType.DISK)));
        given(getSystemMetrics.execute()).willReturn(new SystemMetrics(0, 0, 1, 90, 100, 0, 0));
        given(events.findOpenByTypeAndProjectAndService(any(), any(), any())).willReturn(Optional.empty());
        given(events.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        evaluateAlerts.execute();

        ArgumentCaptor<AlertEventEntity> captor = ArgumentCaptor.forClass(AlertEventEntity.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getRule().getType()).isEqualTo(AlertType.DISK);
        assertThat(captor.getValue().getStatus()).isEqualTo(AlertStatus.ACTIVE);
    }

    private static Optional<AlertEventEntity> openOfType(List<AlertEventEntity> saved, AlertType type) {
        return saved.stream()
                .filter(event -> event.getStatus() == AlertStatus.ACTIVE
                        || event.getStatus() == AlertStatus.ACKNOWLEDGED)
                .filter(event -> event.getRule().getType() == type)
                .findFirst();
    }

    private static AlertRuleEntity rule(AlertType type) {
        return new AlertRuleEntity(UUID.randomUUID(), null, type, Map.of(), true);
    }

    private ContainerInventory inventory() {
        return new ContainerInventory() {
            @Override
            public List<ContainerSnapshot> listAll() {
                return List.copyOf(inventory);
            }

            @Override
            public Optional<ContainerSnapshot> findById(String containerId) {
                return inventory.stream().filter(snapshot -> snapshot.id().equals(containerId)).findFirst();
            }
        };
    }

    private static ContainerSnapshot snapshot(String state) {
        return snapshot(state, 0);
    }

    private static ContainerSnapshot snapshot(String state, int restartCount) {
        return new ContainerSnapshot(
                "web-id",
                "lab-web-1",
                "nginx:alpine",
                state,
                state,
                "running".equals(state) ? "healthy" : null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("nexus.project", "lab", "nexus.service", "web"),
                List.of(),
                restartCount,
                Instant.parse("2026-01-01T00:00:01Z"));
    }
}
