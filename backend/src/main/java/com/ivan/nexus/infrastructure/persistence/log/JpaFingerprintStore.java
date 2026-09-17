package com.ivan.nexus.infrastructure.persistence.log;

import com.ivan.nexus.application.log.FingerprintStore;
import com.ivan.nexus.application.log.StoredErrorFingerprint;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class JpaFingerprintStore implements FingerprintStore {
    private final LogErrorFingerprintJpaRepository repository;

    public JpaFingerprintStore(LogErrorFingerprintJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<StoredErrorFingerprint> findByProjectIdAndServiceIdAndFingerprint(
            String projectId, String serviceId, String fingerprint) {
        return repository
                .findByProjectIdAndServiceIdAndFingerprint(projectId, serviceId, fingerprint)
                .map(JpaFingerprintStore::toRecord);
    }

    @Override
    public StoredErrorFingerprint save(StoredErrorFingerprint fingerprint) {
        return toRecord(repository.save(toEntity(fingerprint)));
    }

    @Override
    public List<StoredErrorFingerprint> findTop10ByProjectIdAndLastSeenAfterOrderByLastSeenDesc(
            String projectId, Instant cutoff) {
        return repository
                .findTop10ByProjectIdAndLastSeenAfterOrderByLastSeenDesc(projectId, cutoff)
                .stream()
                .map(JpaFingerprintStore::toRecord)
                .toList();
    }

    @Override
    public List<StoredErrorFingerprint> findTop50ByProjectIdAndLastSeenAfterOrderByLastSeenDesc(
            String projectId, Instant cutoff) {
        return repository
                .findTop50ByProjectIdAndLastSeenAfterOrderByLastSeenDesc(projectId, cutoff)
                .stream()
                .map(JpaFingerprintStore::toRecord)
                .toList();
    }

    @Override
    public List<StoredErrorFingerprint> findTop50ByProjectIdAndServiceIdAndLastSeenAfterOrderByLastSeenDesc(
            String projectId, String serviceId, Instant cutoff) {
        return repository
                .findTop50ByProjectIdAndServiceIdAndLastSeenAfterOrderByLastSeenDesc(projectId, serviceId, cutoff)
                .stream()
                .map(JpaFingerprintStore::toRecord)
                .toList();
    }

    @Override
    public List<StoredErrorFingerprint> findAll() {
        return repository.findAll().stream().map(JpaFingerprintStore::toRecord).toList();
    }

    @Override
    public long deleteByLastSeenBefore(Instant cutoff) {
        return repository.deleteByLastSeenBefore(cutoff);
    }

    private static StoredErrorFingerprint toRecord(LogErrorFingerprintEntity entity) {
        return new StoredErrorFingerprint(
                entity.getId(),
                entity.getProjectId(),
                entity.getServiceId(),
                entity.getFingerprint(),
                entity.getFirstSeen(),
                entity.getLastSeen(),
                entity.getCount(),
                entity.getSampleMessage());
    }

    private static LogErrorFingerprintEntity toEntity(StoredErrorFingerprint fingerprint) {
        return new LogErrorFingerprintEntity(
                fingerprint.id(),
                fingerprint.projectId(),
                fingerprint.serviceId(),
                fingerprint.fingerprint(),
                fingerprint.firstSeen(),
                fingerprint.lastSeen(),
                fingerprint.count(),
                fingerprint.sampleMessage());
    }
}
