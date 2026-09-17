package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.deployment.HealthChecker;
import com.ivan.nexus.application.log.FingerprintStore;
import com.ivan.nexus.application.log.StoredErrorFingerprint;
import com.ivan.nexus.application.metrics.ContainerStatsProvider;
import com.ivan.nexus.application.metrics.GetSystemMetrics;
import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.alert.AlertEvaluator;
import com.ivan.nexus.domain.alert.AlertEvaluation;
import com.ivan.nexus.domain.alert.AlertFacts;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.alert.ContainerAlertState;
import com.ivan.nexus.domain.alert.ErrorRateState;
import com.ivan.nexus.domain.alert.HttpHealthState;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.metrics.ContainerMetrics;
import com.ivan.nexus.domain.metrics.SystemMetrics;
import com.ivan.nexus.domain.project.Project;
import com.ivan.nexus.domain.project.ProjectGrouping;
import com.ivan.nexus.infrastructure.manifest.YamlManifestLoader;
import com.ivan.nexus.infrastructure.persistence.alert.AlertRuleEntity;
import com.ivan.nexus.infrastructure.persistence.alert.AlertRuleJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "nexus.alerts.enabled", havingValue = "true")
public class EvaluateAlerts {
    private static final Logger log = LoggerFactory.getLogger(EvaluateAlerts.class);
    private static final Duration DEFAULT_HEALTH_TIMEOUT = Duration.ofSeconds(5);

    private final DiscoverProjects discoverProjects;
    private final ContainerStatsProvider statsProvider;
    private final GetSystemMetrics getSystemMetrics;
    private final FingerprintStore fingerprints;
    private final HealthChecker healthChecker;
    private final YamlManifestLoader loader;
    private final AlertRuleJpaRepository rules;
    private final PersistAlertEvaluation persistAlertEvaluation;
    private final RecordActivity recordActivity;
    private final Path allowedRoot;
    private final AlertEvaluator evaluator = new AlertEvaluator();
    private final ConcurrentHashMap<String, ContainerSnapshot> previousSnapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<FingerprintCountKey, Long> previousFingerprintCounts = new ConcurrentHashMap<>();
    private volatile boolean primed;

    public EvaluateAlerts(
            DiscoverProjects discoverProjects,
            ContainerStatsProvider statsProvider,
            GetSystemMetrics getSystemMetrics,
            FingerprintStore fingerprints,
            HealthChecker healthChecker,
            YamlManifestLoader loader,
            AlertRuleJpaRepository rules,
            PersistAlertEvaluation persistAlertEvaluation,
            RecordActivity recordActivity,
            @Value("${nexus.manifest.allowed-root}") String allowedRoot) {
        this.discoverProjects = discoverProjects;
        this.statsProvider = statsProvider;
        this.getSystemMetrics = getSystemMetrics;
        this.fingerprints = fingerprints;
        this.healthChecker = healthChecker;
        this.loader = loader;
        this.rules = rules;
        this.persistAlertEvaluation = persistAlertEvaluation;
        this.recordActivity = recordActivity;
        this.allowedRoot = Path.of(allowedRoot).toAbsolutePath().normalize();
    }

    @Scheduled(fixedDelayString = "${nexus.alerts.interval-ms:30000}")
    public void execute() {
        Instant now = Instant.now();
        List<AlertRuleEntity> enabledRules = rules.findByEnabledTrue();
        Map<String, List<ContainerSnapshot>> grouped = discoverProjects.groupByProject();
        Map<String, ProjectManifest> manifests = loadManifests();

        List<ContainerAlertState> current = new ArrayList<>();
        List<ContainerAlertState> previous = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            String projectId = entry.getKey();
            int memoryThreshold = memoryThreshold(enabledRules, projectId, manifests.get(projectId));
            for (ContainerSnapshot container : entry.getValue()) {
                String serviceId = serviceId(container);
                current.add(toState(container, projectId, serviceId, memoryThreshold));
                ContainerSnapshot prior = previousSnapshots.get(container.id());
                if (prior != null) {
                    previous.add(toState(prior, projectId, serviceId, memoryThreshold));
                }
            }
        }

        AlertFacts facts = new AlertFacts(
                now,
                previous,
                current,
                diskPercent(),
                diskThreshold(enabledRules),
                errorRates(enabledRules, manifests),
                httpHealthChecks(enabledRules, manifests));

        AlertEvaluation evaluation = evaluator.evaluate(facts);
        persistAlertEvaluation.persist(evaluation, enabledRules, now);
        emitContainerActivity(grouped);

        previousSnapshots.clear();
        for (List<ContainerSnapshot> containers : grouped.values()) {
            for (ContainerSnapshot container : containers) {
                previousSnapshots.put(container.id(), container);
            }
        }
        primed = true;
    }

    private void emitContainerActivity(Map<String, List<ContainerSnapshot>> grouped) {
        for (var entry : grouped.entrySet()) {
            String projectId = entry.getKey();
            for (ContainerSnapshot current : entry.getValue()) {
                String serviceId = serviceId(current);
                ContainerSnapshot previous = previousSnapshots.get(current.id());
                if (previous == null) {
                    if (primed && isRunning(current.state())) {
                        recordContainer(ActivityType.CONTAINER_STARTED, projectId, serviceId, current, "started");
                    }
                    continue;
                }
                if (current.restartCount() > previous.restartCount()) {
                    recordContainer(ActivityType.CONTAINER_RESTARTED, projectId, serviceId, current, "restarted");
                    continue;
                }
                boolean wasRunning = isRunning(previous.state());
                boolean nowRunning = isRunning(current.state());
                if (wasRunning && !nowRunning) {
                    recordContainer(ActivityType.CONTAINER_STOPPED, projectId, serviceId, current, "stopped");
                } else if (!wasRunning && nowRunning) {
                    recordContainer(ActivityType.CONTAINER_STARTED, projectId, serviceId, current, "started");
                }
            }
        }
    }

    private void recordContainer(
            ActivityType type,
            String projectId,
            String serviceId,
            ContainerSnapshot container,
            String message) {
        recordActivity.execute(
                type,
                projectId,
                serviceId,
                message,
                Map.of("containerId", container.id(), "restartCount", container.restartCount()));
    }

    private static boolean isRunning(String state) {
        return state != null && state.equalsIgnoreCase("running");
    }

    private ContainerAlertState toState(
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

    private Double memoryPercent(String containerId) {
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

    private double diskPercent() {
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

    private List<ErrorRateState> errorRates(List<AlertRuleEntity> enabledRules, Map<String, ProjectManifest> manifests) {
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

    private List<HttpHealthState> httpHealthChecks(
            List<AlertRuleEntity> enabledRules,
            Map<String, ProjectManifest> manifests) {
        if (ruleFor(enabledRules, AlertType.HTTP_HEALTH, null) == null) {
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

    private Map<String, ProjectManifest> loadManifests() {
        Map<String, ProjectManifest> manifests = new HashMap<>();
        for (Project project : discoverProjects.execute()) {
            if (!project.deployable()) {
                continue;
            }
            Path path = allowedRoot.resolve(project.id()).resolve("nexus.yml");
            try {
                manifests.put(project.id(), loader.load(path));
            } catch (RuntimeException ex) {
                log.warn("Unable to load manifest for project {}", project.id(), ex);
            }
        }
        return manifests;
    }

    private static int memoryThreshold(
            List<AlertRuleEntity> enabledRules,
            String projectId,
            ProjectManifest manifest) {
        AlertRuleEntity rule = ruleFor(enabledRules, AlertType.HIGH_MEMORY, projectId);
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

    private static int diskThreshold(List<AlertRuleEntity> enabledRules) {
        AlertRuleEntity rule = ruleFor(enabledRules, AlertType.DISK, null);
        if (rule != null) {
            OptionalInt fromRule = rule.threshold("diskPercent");
            if (fromRule.isPresent()) {
                return fromRule.getAsInt();
            }
        }
        return AlertEvaluator.DEFAULT_DISK_PERCENT;
    }

    private static int errorRateThreshold(
            List<AlertRuleEntity> enabledRules,
            String projectId,
            ProjectManifest manifest) {
        AlertRuleEntity rule = ruleFor(enabledRules, AlertType.ERROR_RATE, projectId);
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

    static AlertRuleEntity ruleFor(List<AlertRuleEntity> enabledRules, AlertType type, String projectId) {
        AlertRuleEntity global = null;
        for (AlertRuleEntity rule : enabledRules) {
            if (rule.getType() != type) {
                continue;
            }
            if (projectId != null && projectId.equals(rule.getProjectId())) {
                return rule;
            }
            if (rule.getProjectId() == null) {
                global = rule;
            }
        }
        return global;
    }

    private static String serviceId(ContainerSnapshot container) {
        return ProjectGrouping.serviceId(container.name(), container.labels());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record FingerprintCountKey(String projectId, String serviceId, String fingerprint) {
    }

    private record ServiceKey(String projectId, String serviceId) {
    }
}
