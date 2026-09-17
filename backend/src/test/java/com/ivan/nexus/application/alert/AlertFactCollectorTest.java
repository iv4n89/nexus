package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.deployment.HealthChecker;
import com.ivan.nexus.application.log.FingerprintStore;
import com.ivan.nexus.application.log.StoredErrorFingerprint;
import com.ivan.nexus.application.metrics.ContainerStatsProvider;
import com.ivan.nexus.application.metrics.GetSystemMetrics;
import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.domain.alert.AlertEvaluator;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.alert.ErrorRateState;
import com.ivan.nexus.domain.alert.HttpHealthState;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.metrics.ContainerMetrics;
import com.ivan.nexus.domain.metrics.SystemMetrics;
import com.ivan.nexus.domain.project.Project;
import com.ivan.nexus.infrastructure.persistence.alert.AlertRuleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertFactCollectorTest {

    @Mock
    DiscoverProjects discoverProjects;
    @Mock
    ContainerStatsProvider statsProvider;
    @Mock
    GetSystemMetrics getSystemMetrics;
    @Mock
    FingerprintStore fingerprints;
    @Mock
    HealthChecker healthChecker;

    private AlertFactCollector collector;

    @BeforeEach
    void setUp() {
        collector = new AlertFactCollector(
                discoverProjects, statsProvider, getSystemMetrics, fingerprints, healthChecker);
    }

    @Test
    void memoryPercentUsesUsedOverLimit() {
        given(statsProvider.stats("web-id")).willReturn(new ContainerMetrics("web-id", 0, 95, 100, 0, 0));
        assertThat(collector.memoryPercent("web-id")).isEqualTo(95.0);
    }

    @Test
    void memoryPercentIsNullWhenLimitMissing() {
        given(statsProvider.stats("web-id")).willReturn(new ContainerMetrics("web-id", 0, 95, 0, 0, 0));
        assertThat(collector.memoryPercent("web-id")).isNull();
    }

    @Test
    void diskPercentUsesUsedOverTotal() {
        given(getSystemMetrics.execute()).willReturn(new SystemMetrics(0, 0, 1, 90, 100, 0, 0));
        assertThat(collector.diskPercent()).isEqualTo(90.0);
    }

    @Test
    void diskPercentIsZeroWhenTotalMissing() {
        given(getSystemMetrics.execute()).willReturn(new SystemMetrics(0, 0, 1, 90, 0, 0, 0));
        assertThat(collector.diskPercent()).isEqualTo(0.0);
    }

    @Test
    void errorRatesUseCountDeltaAndDefaultThreshold() {
        given(fingerprints.findAll()).willReturn(List.of(fingerprint(12)));
        List<ErrorRateState> first = collector.errorRates(List.of(), Map.of());
        assertThat(first).containsExactly(new ErrorRateState("lab", "web", 12, AlertEvaluator.DEFAULT_ERROR_RATE_PER_MINUTE));

        given(fingerprints.findAll()).willReturn(List.of(fingerprint(15)));
        List<ErrorRateState> second = collector.errorRates(List.of(), Map.of());
        assertThat(second).containsExactly(new ErrorRateState("lab", "web", 3, AlertEvaluator.DEFAULT_ERROR_RATE_PER_MINUTE));
    }

    @Test
    void httpHealthChecksAreEmptyWithoutRule() {
        assertThat(collector.httpHealthChecks(List.of(), Map.of())).isEmpty();
    }

    @Test
    void httpHealthChecksUseManifestUrlAndTimeout() {
        given(discoverProjects.execute()).willReturn(List.of(new Project("lab", "lab", "running", 1, 1, true)));
        when(healthChecker.check(eq("http://127.0.0.1:18080"), eq(Duration.ofSeconds(5)))).thenReturn(false);

        List<HttpHealthState> checks = collector.httpHealthChecks(
                List.of(rule(AlertType.HTTP_HEALTH, Map.of())),
                Map.of("lab", manifest("http://127.0.0.1:18080", 5)));

        assertThat(checks).containsExactly(new HttpHealthState("lab", false));
        verify(healthChecker).check("http://127.0.0.1:18080", Duration.ofSeconds(5));
    }

    @Test
    void memoryThresholdPrefersRuleThenManifestThenDefault() {
        AlertRuleEntity rule = rule(AlertType.HIGH_MEMORY, Map.of("memoryPercent", 70));
        assertThat(AlertFactCollector.memoryThreshold(List.of(rule), "lab", manifest(null, null))).isEqualTo(70);
        assertThat(AlertFactCollector.memoryThreshold(List.of(), "lab", alertsManifest(80, null))).isEqualTo(80);
        assertThat(AlertFactCollector.memoryThreshold(List.of(), "lab", null))
                .isEqualTo(AlertEvaluator.DEFAULT_MEMORY_PERCENT);
    }

    @Test
    void diskThresholdPrefersRuleThenDefault() {
        AlertRuleEntity rule = rule(AlertType.DISK, Map.of("diskPercent", 60));
        assertThat(AlertFactCollector.diskThreshold(List.of(rule))).isEqualTo(60);
        assertThat(AlertFactCollector.diskThreshold(List.of())).isEqualTo(AlertEvaluator.DEFAULT_DISK_PERCENT);
    }

    @Test
    void toStateCopiesContainerFieldsAndMemory() {
        given(statsProvider.stats("web-id")).willReturn(new ContainerMetrics("web-id", 0, 40, 100, 0, 0));
        ContainerSnapshot container = new ContainerSnapshot(
                "web-id",
                "lab-web-1",
                "nginx:alpine",
                "running",
                "running",
                "healthy",
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("nexus.project", "lab", "nexus.service", "web"),
                List.of(),
                2,
                Instant.parse("2026-01-01T00:00:01Z"));

        var state = collector.toState(container, "lab", "web", 90);
        assertThat(state.containerId()).isEqualTo("web-id");
        assertThat(state.projectId()).isEqualTo("lab");
        assertThat(state.serviceId()).isEqualTo("web");
        assertThat(state.state()).isEqualTo("running");
        assertThat(state.health()).isEqualTo("healthy");
        assertThat(state.restartCount()).isEqualTo(2);
        assertThat(state.memoryPercent()).isEqualTo(40.0);
        assertThat(state.memoryThreshold()).isEqualTo(90);
    }

    private static StoredErrorFingerprint fingerprint(long count) {
        return new StoredErrorFingerprint(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "lab",
                "web",
                "boom",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z"),
                count,
                "boom");
    }

    private static AlertRuleEntity rule(AlertType type, Map<String, Object> threshold) {
        return new AlertRuleEntity(UUID.randomUUID(), null, type, threshold, true);
    }

    private static ProjectManifest manifest(String healthUrl, Integer timeoutSeconds) {
        return new ProjectManifest(
                new ProjectManifest.ProjectBlock("lab", "Lab", null, "/tmp/lab"),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                healthUrl == null ? null : new ProjectManifest.HealthBlock(healthUrl, timeoutSeconds),
                null);
    }

    private static ProjectManifest alertsManifest(Integer memoryPercent, Integer errorRate) {
        return new ProjectManifest(
                new ProjectManifest.ProjectBlock("lab", "Lab", null, "/tmp/lab"),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                new ProjectManifest.AlertsBlock(errorRate, null, memoryPercent));
    }
}
