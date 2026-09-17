package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.deployment.HealthChecker;
import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import com.ivan.nexus.application.metrics.ContainerStatsProvider;
import com.ivan.nexus.application.metrics.GetSystemMetrics;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertKey;
import com.ivan.nexus.domain.alert.AlertRule;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.metrics.ContainerMetrics;
import com.ivan.nexus.domain.metrics.SystemMetrics;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
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
    AlertRuleStore rules;
    @Mock
    AlertStore alerts;
    @Mock
    RecordActivity recordActivity;

    private final List<ContainerSnapshot> inventory = new ArrayList<>();
    private FakeManifestCatalog manifests;
    private EvaluateAlerts evaluateAlerts;

    @BeforeEach
    void setUp() {
        given(getSystemMetrics.execute()).willReturn(new SystemMetrics(0, 0, 1, 10, 100, 0, 0));
        given(fingerprints.findAll()).willReturn(List.of());
        given(rules.findEnabled()).willReturn(List.of(rule(AlertType.CONTAINER_STOPPED)));
        manifests = new FakeManifestCatalog();
        DiscoverProjects discoverProjects = new DiscoverProjects(inventory(), manifests);
        evaluateAlerts = new EvaluateAlerts(
                discoverProjects,
                manifests,
                rules,
                new PersistAlertEvaluation(alerts, recordActivity),
                new AlertFactCollector(
                        discoverProjects, statsProvider, getSystemMetrics, fingerprints, healthChecker),
                new ContainerLifecycleNotifier(recordActivity));
    }

    @Test
    void persistsActiveAlertWhenContainerStops() {
        given(alerts.findOpen(any())).willReturn(Optional.empty());

        inventory.add(snapshot("running"));
        evaluateAlerts.execute();

        inventory.clear();
        inventory.add(snapshot("exited"));
        evaluateAlerts.execute();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alerts).open(captor.capture());
        Alert saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(saved.projectId()).isEqualTo("lab");
        assertThat(saved.serviceId()).isEqualTo("web");
        assertThat(saved.type()).isEqualTo(AlertType.CONTAINER_STOPPED);
        assertThat(saved.resolvedAt()).isNull();
        verify(recordActivity).execute(
                eq(ActivityType.ALERT_CREATED), eq("lab"), eq("web"), eq("alert created"), any());
        verify(recordActivity).execute(
                eq(ActivityType.CONTAINER_STOPPED), eq("lab"), eq("web"), eq("stopped"), any());
    }

    @Test
    void doesNotDuplicateOpenAlertWhenConditionStillHolds() {
        List<Alert> saved = new ArrayList<>();
        stubAlertStore(saved);

        inventory.add(snapshot("running"));
        evaluateAlerts.execute();
        inventory.clear();
        inventory.add(snapshot("exited"));
        evaluateAlerts.execute();
        evaluateAlerts.execute();

        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().status()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(saved.getFirst().resolvedAt()).isNull();
    }

    @Test
    void resolvesOpenAlertWhenConditionClears() {
        List<Alert> saved = new ArrayList<>();
        stubAlertStore(saved);

        inventory.add(snapshot("running"));
        evaluateAlerts.execute();
        inventory.clear();
        inventory.add(snapshot("exited"));
        evaluateAlerts.execute();
        inventory.clear();
        inventory.add(snapshot("running"));
        evaluateAlerts.execute();

        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().status()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(saved.getFirst().resolvedAt()).isNotNull();
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
        given(rules.findEnabled()).willReturn(List.of(rule(AlertType.HIGH_MEMORY)));
        given(statsProvider.stats("web-id")).willReturn(new ContainerMetrics("web-id", 0, 95, 100, 0, 0));
        given(alerts.findOpen(any())).willReturn(Optional.empty());

        inventory.add(snapshot("running"));
        evaluateAlerts.execute();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alerts).open(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AlertType.HIGH_MEMORY);
        assertThat(captor.getValue().status()).isEqualTo(AlertStatus.ACTIVE);
    }

    @Test
    void persistsDiskAlertWhenUsageExceedsDefaultThreshold() {
        given(rules.findEnabled()).willReturn(List.of(rule(AlertType.DISK)));
        given(getSystemMetrics.execute()).willReturn(new SystemMetrics(0, 0, 1, 90, 100, 0, 0));
        given(alerts.findOpen(any())).willReturn(Optional.empty());

        evaluateAlerts.execute();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alerts).open(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AlertType.DISK);
        assertThat(captor.getValue().status()).isEqualTo(AlertStatus.ACTIVE);
    }

    @Test
    void invalidManifestIsSkippedWithoutStoppingEvaluation() {
        manifests.fail("lab", new DomainException(NexusErrorCode.MANIFEST_INVALID, "invalid fixture"));
        inventory.add(snapshot("running"));

        assertThatCode(evaluateAlerts::execute).doesNotThrowAnyException();
    }

    private static Optional<Alert> openOfType(List<Alert> saved, AlertKey key) {
        return saved.stream()
                .filter(event -> event.status() == AlertStatus.ACTIVE
                        || event.status() == AlertStatus.ACKNOWLEDGED)
                .filter(event -> event.type() == key.type())
                .findFirst();
    }

    private void stubAlertStore(List<Alert> saved) {
        given(alerts.findOpen(any())).willAnswer(invocation -> openOfType(saved, invocation.getArgument(0)));
        doAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return null;
        }).when(alerts).open(any());
        given(alerts.resolve(any(), any())).willAnswer(invocation -> {
            AlertKey key = invocation.getArgument(0);
            Optional<Alert> open = openOfType(saved, key);
            open.ifPresent(alert -> {
                saved.remove(alert);
                saved.add(alert.resolve(invocation.getArgument(1)));
            });
            return open.map(alert -> alert.resolve(invocation.getArgument(1)));
        });
    }

    private static AlertRule rule(AlertType type) {
        return new AlertRule(UUID.randomUUID(), null, type, Map.of(), true);
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
