package com.ivan.nexus.application.log;

import com.ivan.nexus.domain.log.ErrorNormalizer;
import com.ivan.nexus.infrastructure.persistence.log.LogErrorFingerprintEntity;
import com.ivan.nexus.infrastructure.persistence.log.LogErrorFingerprintJpaRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class GetRecentErrors {
    static final Duration RECENT_WINDOW = Duration.ofHours(24);
    private final LogErrorFingerprintJpaRepository fingerprints;

    public GetRecentErrors(LogErrorFingerprintJpaRepository fingerprints) {
        this.fingerprints = fingerprints;
    }

    public List<RecentError> execute(String projectId) {
        return fingerprints
                .findTop10ByProjectIdAndLastSeenAfterOrderByLastSeenDesc(projectId, cutoff())
                .stream()
                .filter(GetRecentErrors::isActionable)
                .map(GetRecentErrors::toRecentError)
                .toList();
    }

    public List<RecentError> execute(String projectId, String serviceId) {
        Instant cutoff = cutoff();
        if (serviceId == null || serviceId.isBlank()) {
            return fingerprints
                    .findTop50ByProjectIdAndLastSeenAfterOrderByLastSeenDesc(projectId, cutoff)
                    .stream()
                    .filter(GetRecentErrors::isActionable)
                    .map(GetRecentErrors::toRecentError)
                    .toList();
        }
        return fingerprints
                .findTop50ByProjectIdAndServiceIdAndLastSeenAfterOrderByLastSeenDesc(
                        projectId, serviceId, cutoff)
                .stream()
                .filter(GetRecentErrors::isActionable)
                .map(GetRecentErrors::toRecentError)
                .toList();
    }

    private static boolean isActionable(LogErrorFingerprintEntity entity) {
        return ErrorNormalizer.normalize(entity.getSampleMessage()).isPresent();
    }

    private static Instant cutoff() {
        return Instant.now().minus(RECENT_WINDOW);
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
