package com.ivan.nexus.application.log;

import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.log.ErrorFingerprint;
import com.ivan.nexus.domain.log.ErrorNormalizer;
import com.ivan.nexus.domain.project.ProjectGrouping;
import com.ivan.nexus.infrastructure.persistence.log.LogErrorFingerprintEntity;
import com.ivan.nexus.infrastructure.persistence.log.LogErrorFingerprintJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "nexus.logs.analysis-enabled", havingValue = "true", matchIfMissing = true)
public class AnalyzeLogs {
    private static final Logger log = LoggerFactory.getLogger(AnalyzeLogs.class);

    private final DiscoverProjects discoverProjects;
    private final LogProvider logProvider;
    private final LogErrorFingerprintJpaRepository fingerprints;
    private final RecordActivity recordActivity;
    private final int logWindowSeconds;
    private final ConcurrentHashMap<String, Integer> watermarks = new ConcurrentHashMap<>();

    public AnalyzeLogs(
            DiscoverProjects discoverProjects,
            LogProvider logProvider,
            LogErrorFingerprintJpaRepository fingerprints,
            RecordActivity recordActivity,
            @Value("${nexus.alerts.log-window-seconds:120}") int logWindowSeconds) {
        this.discoverProjects = discoverProjects;
        this.logProvider = logProvider;
        this.fingerprints = fingerprints;
        this.recordActivity = recordActivity;
        this.logWindowSeconds = logWindowSeconds;
    }

    @Scheduled(fixedDelayString = "${nexus.alerts.interval-ms:30000}")
    public void execute() {
        int nowEpoch = (int) Instant.now().getEpochSecond();
        Instant now = Instant.now();
        Map<FingerprintKey, LogErrorFingerprintEntity> pending = new HashMap<>();
        for (var entry : discoverProjects.groupByProject().entrySet()) {
            String projectId = entry.getKey();
            for (ContainerSnapshot container : entry.getValue()) {
                if (!"running".equalsIgnoreCase(container.state())) {
                    continue;
                }
                String serviceId = serviceId(container);
                int since = sinceFor(container.id(), nowEpoch);
                List<String> lines;
                try {
                    lines = logProvider.fetch(container.id(), GetContainerLogs.MAX_TAIL, since, null, false);
                } catch (RuntimeException ex) {
                    log.warn("Failed to fetch logs for container {}", container.id(), ex);
                    continue;
                }
                watermarks.put(container.id(), nowEpoch);
                for (String line : lines) {
                    ErrorNormalizer.normalize(line).ifPresent(error -> upsert(pending, projectId, serviceId, error, now));
                }
            }
        }
    }

    private int sinceFor(String containerId, int nowEpoch) {
        int windowStart = nowEpoch - logWindowSeconds;
        Integer lastWatermark = watermarks.get(containerId);
        int since = lastWatermark == null ? windowStart : Math.max(windowStart, lastWatermark);
        return since;
    }

    private void upsert(
            Map<FingerprintKey, LogErrorFingerprintEntity> pending,
            String projectId,
            String serviceId,
            ErrorFingerprint error,
            Instant now) {
        FingerprintKey key = new FingerprintKey(projectId, serviceId, error.fingerprint());
        LogErrorFingerprintEntity entity = pending.get(key);
        boolean created = false;
        if (entity == null) {
            entity = fingerprints
                    .findByProjectIdAndServiceIdAndFingerprint(projectId, serviceId, error.fingerprint())
                    .orElse(null);
        }
        if (entity == null) {
            entity = new LogErrorFingerprintEntity(
                    UUID.randomUUID(),
                    projectId,
                    serviceId,
                    error.fingerprint(),
                    now,
                    now,
                    1L,
                    error.sampleMessage());
            created = true;
        } else {
            entity.recordHit(now);
        }
        pending.put(key, entity);
        fingerprints.save(entity);
        if (created) {
            recordActivity.execute(
                    ActivityType.ERROR_DETECTED,
                    projectId,
                    serviceId,
                    "error detected",
                    Map.of("fingerprint", error.fingerprint(), "sampleMessage", error.sampleMessage()));
        }
    }

    private static String serviceId(ContainerSnapshot container) {
        String serviceId = ProjectGrouping.serviceId(container.name(), container.labels());
        return serviceId == null ? "" : serviceId;
    }

    private record FingerprintKey(String projectId, String serviceId, String fingerprint) {
    }
}
