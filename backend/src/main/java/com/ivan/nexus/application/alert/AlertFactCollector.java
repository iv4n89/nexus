package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.deployment.HealthChecker;
import com.ivan.nexus.application.log.FingerprintStore;
import com.ivan.nexus.application.log.StoredErrorFingerprint;
import com.ivan.nexus.application.metrics.ContainerStatsProvider;
import com.ivan.nexus.application.metrics.GetSystemMetrics;
import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.domain.alert.AlertEvaluator;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.alert.ContainerAlertState;
import com.ivan.nexus.domain.alert.ErrorRateState;
import com.ivan.nexus.domain.alert.HttpHealthState;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.metrics.ContainerMetrics;
import com.ivan.nexus.domain.metrics.SystemMetrics;
import com.ivan.nexus.domain.project.Project;
import com.ivan.nexus.infrastructure.persistence.alert.AlertRuleEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlertFactCollector {
    private static final Logger log = LoggerFactory.getLogger(AlertFactCollector.class);
    private static final Duration DEFAULT_HEALTH_TIMEOUT = Duration.ofSeconds(5);

    private final DiscoverProjects discoverProjects;
    private final ContainerStatsProvider statsProvider;
    private final GetSystemMetrics getSystemMetrics;
    private final FingerprintStore fingerprints;
    private final HealthChecker healthChecker;
    private final ConcurrentHashMap<FingerprintCountKey, Long> previousFingerprintCounts = new ConcurrentHashMap<>();

    public AlertFactCollector(
            DiscoverProjects discoverProjects,
            ContainerStatsProvider statsProvider,
            GetSystemMetrics getSystemMetrics,
            FingerprintStore fingerprints,
            HealthChecker healthChecker) {
        this.discoverProjects = discoverProjects;
        this.statsProvider = statsProvider;
        this.getSystemMetrics = getSystemMetrics;
        this.fingerprints = fingerprints;
        this.healthChecker = healthChecker;
    }

    ContainerAlertState toState(
            ContainerSnapshot container,
            String projectId,
            String serviceId,
            int memoryThreshold) {
        return new ContainerAlertState(
                container.id(),
                projectId,
                serviceId,
                container.state(),
                container.health(),
                container.restartCount(),
                memoryPercent(container.id()),
                memoryThreshold);
    }

    Double memoryPercent(String containerId) {
        try {
            ContainerMetrics metrics = statsProvider.stats(containerId);
            if (metrics == null || metrics.memoryLimitBytes() <= 0) {
                return null;
            }
            return metrics.memoryUsedBytes() * 100.0 / metrics.memoryLimitBytes();
        } catch (RuntimeException ex) {
            log.warn("Skipping memory stats for container {}", containerId, ex);
            return null;
        }
    }

    double diskPercent() {
        try {
            SystemMetrics metrics = getSystemMetrics.execute();
            if (metrics.diskTotalBytes() <= 0) {
                return 0;
            }
            return metrics.diskUsedBytes() * 100.0 / metrics.diskTotalBytes();
        } catch (RuntimeException ex) {
            log.warn("Skipping host disk metrics", ex);
            return 0;
        }
    }

    List<ErrorRateState> errorRates(List<AlertRuleEntity> enabledRules, Map<String, ProjectManifest> manifests) {
        Map<ServiceKey, Integer> deltas = new HashMap<>();
        for (StoredErrorFingerprint entity : fingerprints.findAll()) {
            FingerprintCountKey countKey = new FingerprintCountKey(
                    entity.projectId(), entity.serviceId(), entity.fingerprint());
            long previous = previousFingerprintCounts.getOrDefault(countKey, 0L);
            int delta = (int) Math.max(0, entity.count() - previous);
            previousFingerprintCounts.put(countKey, entity.count());
            ServiceKey serviceKey = new ServiceKey(entity.projectId(), entity.serviceId());
            deltas.merge(serviceKey, delta, Integer::sum);
        }
        for (FingerprintCountKey countKey : previousFingerprintCounts.keySet()) {
            deltas.putIfAbsent(new ServiceKey(countKey.projectId(), countKey.serviceId()), 0);
        }
        List<ErrorRateState> states = new ArrayList<>();
        for (var entry : deltas.entrySet()) {
            states.add(new ErrorRateState(
                    entry.getKey().projectId(),
                    entry.getKey().serviceId(),
                    entry.getValue(),
                    errorRateThreshold(enabledRules, entry.getKey().projectId(), manifests.get(entry.getKey().projectId()))));
        }
        return states;
    }

    List<HttpHealthState> httpHealthChecks(
            List<AlertRuleEntity> enabledRules,
            Map<String, ProjectManifest> manifests) {
        if (EvaluateAlerts.ruleFor(enabledRules, AlertType.HTTP_HEALTH, null) == null) {
            return List.of();
        }
        List<HttpHealthState> checks = new ArrayList<>();
        for (Project project : discoverProjects.execute()) {
            ProjectManifest manifest = manifests.get(project.id());
            if (manifest == null || manifest.health() == null || isBlank(manifest.health().url())) {
                continue;
            }
            Duration timeout = manifest.health().timeoutSeconds() == null
                    ? DEFAULT_HEALTH_TIMEOUT
                    : Duration.ofSeconds(manifest.health().timeoutSeconds());
            boolean healthy;
            try {
                healthy = healthChecker.check(manifest.health().url(), timeout);
            } catch (RuntimeException ex) {
                log.warn("HTTP health check failed for project {}", project.id(), ex);
                healthy = false;
            }
            checks.add(new HttpHealthState(project.id(), healthy));
        }
        return checks;
    }

    static int memoryThreshold(
            List<AlertRuleEntity> enabledRules,
            String projectId,
            ProjectManifest manifest) {
        AlertRuleEntity rule = EvaluateAlerts.ruleFor(enabledRules, AlertType.HIGH_MEMORY, projectId);
        if (rule != null) {
            OptionalInt fromRule = rule.threshold("memoryPercent");
            if (fromRule.isPresent()) {
                return fromRule.getAsInt();
            }
        }
        if (manifest != null && manifest.alerts() != null && manifest.alerts().memoryPercent() != null) {
            return manifest.alerts().memoryPercent();
        }
        return AlertEvaluator.DEFAULT_MEMORY_PERCENT;
    }

    static int diskThreshold(List<AlertRuleEntity> enabledRules) {
        AlertRuleEntity rule = EvaluateAlerts.ruleFor(enabledRules, AlertType.DISK, null);
        if (rule != null) {
            OptionalInt fromRule = rule.threshold("diskPercent");
            if (fromRule.isPresent()) {
                return fromRule.getAsInt();
            }
        }
        return AlertEvaluator.DEFAULT_DISK_PERCENT;
    }

    static int errorRateThreshold(
            List<AlertRuleEntity> enabledRules,
            String projectId,
            ProjectManifest manifest) {
        AlertRuleEntity rule = EvaluateAlerts.ruleFor(enabledRules, AlertType.ERROR_RATE, projectId);
        if (rule != null) {
            OptionalInt fromRule = rule.threshold("errorRatePerMinute");
            if (fromRule.isPresent()) {
                return fromRule.getAsInt();
            }
        }
        if (manifest != null && manifest.alerts() != null && manifest.alerts().errorRatePerMinute() != null) {
            return manifest.alerts().errorRatePerMinute();
        }
        return AlertEvaluator.DEFAULT_ERROR_RATE_PER_MINUTE;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record FingerprintCountKey(String projectId, String serviceId, String fingerprint) {
    }

    private record ServiceKey(String projectId, String serviceId) {
    }
}
