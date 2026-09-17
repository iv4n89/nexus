package com.ivan.nexus.application.log;

import com.ivan.nexus.domain.log.ErrorNormalizer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class GetRecentErrors {
    static final Duration RECENT_WINDOW = Duration.ofHours(24);
    private final FingerprintStore fingerprints;

    public GetRecentErrors(FingerprintStore fingerprints) {
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

    private static boolean isActionable(StoredErrorFingerprint entity) {
        return ErrorNormalizer.normalize(entity.sampleMessage()).isPresent();
    }

    private static Instant cutoff() {
        return Instant.now().minus(RECENT_WINDOW);
    }

    private static RecentError toRecentError(StoredErrorFingerprint entity) {
        return new RecentError(
                entity.serviceId(),
                entity.sampleMessage(),
                entity.count(),
                entity.firstSeen(),
                entity.lastSeen());
    }

    public record RecentError(
            String serviceId, String sampleMessage, long count, Instant firstSeen, Instant lastSeen) {
    }
}
