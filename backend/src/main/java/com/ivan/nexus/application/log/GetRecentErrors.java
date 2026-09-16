package com.ivan.nexus.application.log;

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
                .map(entity -> new RecentError(entity.getSampleMessage(), entity.getCount(), entity.getLastSeen()))
                .toList();
    }

    public record RecentError(String sampleMessage, long count, Instant lastSeen) {
    }
}
