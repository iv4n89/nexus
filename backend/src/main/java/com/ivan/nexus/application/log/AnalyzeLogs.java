package com.ivan.nexus.application.log;

import com.ivan.nexus.application.project.DiscoverProjects;
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

@Service
@ConditionalOnProperty(name = "nexus.alerts.enabled", havingValue = "true")
public class AnalyzeLogs {
    private static final Logger log = LoggerFactory.getLogger(AnalyzeLogs.class);

    private final DiscoverProjects discoverProjects;
    private final LogProvider logProvider;
    private final LogErrorFingerprintJpaRepository fingerprints;
    private final int logWindowSeconds;

    public AnalyzeLogs(
            DiscoverProjects discoverProjects,
            LogProvider logProvider,
            LogErrorFingerprintJpaRepository fingerprints,
            @Value("${nexus.alerts.log-window-seconds:120}") int logWindowSeconds) {
        this.discoverProjects = discoverProjects;
        this.logProvider = logProvider;
        this.fingerprints = fingerprints;
        this.logWindowSeconds = logWindowSeconds;
    }

    @Scheduled(fixedDelayString = "${nexus.alerts.interval-ms:30000}")
    public void execute() {
        int since = (int) (Instant.now().getEpochSecond() - logWindowSeconds);
        Map<FingerprintKey, LogErrorFingerprintEntity> pending = new HashMap<>();
        Instant now = Instant.now();
        for (var entry : discoverProjects.groupByProject().entrySet()) {
            String projectId = entry.getKey();
            for (ContainerSnapshot container : entry.getValue()) {
                String serviceId = serviceId(container);
                for (String line : fetchLines(container.id(), since)) {
                    ErrorNormalizer.normalize(line).ifPresent(error -> upsert(pending, projectId, serviceId, error, now));
                }
            }
        }
    }

    private List<String> fetchLines(String containerId, int since) {
        try {
            return logProvider.fetch(containerId, GetContainerLogs.MAX_TAIL, since, null, false);
        } catch (RuntimeException ex) {
            log.warn("Failed to fetch logs for container {}", containerId, ex);
            return List.of();
        }
    }

    private void upsert(
            Map<FingerprintKey, LogErrorFingerprintEntity> pending,
            String projectId,
            String serviceId,
            ErrorFingerprint error,
            Instant now) {
        FingerprintKey key = new FingerprintKey(projectId, serviceId, error.fingerprint());
        LogErrorFingerprintEntity entity = pending.get(key);
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
        } else {
            entity.recordHit(now);
        }
        pending.put(key, entity);
        fingerprints.save(entity);
    }

    private static String serviceId(ContainerSnapshot container) {
        String serviceId = ProjectGrouping.serviceId(container.name(), container.labels());
        return serviceId == null ? "" : serviceId;
    }

    private record FingerprintKey(String projectId, String serviceId, String fingerprint) {
    }
}
