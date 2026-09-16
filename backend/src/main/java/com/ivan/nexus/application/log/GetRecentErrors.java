package com.ivan.nexus.application.log;

import com.ivan.nexus.infrastructure.persistence.log.LogErrorFingerprintEntity;
import com.ivan.nexus.infrastructure.persistence.log.LogErrorFingerprintJpaRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class GetRecentErrors {
    private final LogErrorFingerprintJpaRepository fingerprints;

    public GetRecentErrors(LogErrorFingerprintJpaRepository fingerprints) {
        this.fingerprints = fingerprints;
    }

    public List<RecentError> execute(String projectId) {
        return fingerprints.findTop10ByProjectIdOrderByLastSeenDesc(projectId).stream()
                .map(GetRecentErrors::toRecentError)
                .toList();
    }

    public List<RecentError> execute(String projectId, String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return fingerprints.findTop50ByProjectIdOrderByLastSeenDesc(projectId).stream()
                    .map(GetRecentErrors::toRecentError)
                    .toList();
        }
        return fingerprints
                .findTop50ByProjectIdAndServiceIdOrderByLastSeenDesc(projectId, serviceId)
                .stream()
                .map(GetRecentErrors::toRecentError)
                .toList();
    }

    private static RecentError toRecentError(LogErrorFingerprintEntity entity) {
        return new RecentError(
                entity.getServiceId(),
                entity.getSampleMessage(),
                entity.getCount(),
                entity.getFirstSeen(),
                entity.getLastSeen());
    }

    public record RecentError(
            String serviceId, String sampleMessage, long count, Instant firstSeen, Instant lastSeen) {
    }
}
