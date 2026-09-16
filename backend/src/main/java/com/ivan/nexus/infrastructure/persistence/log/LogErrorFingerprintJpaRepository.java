package com.ivan.nexus.infrastructure.persistence.log;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LogErrorFingerprintJpaRepository extends JpaRepository<LogErrorFingerprintEntity, UUID> {
    Optional<LogErrorFingerprintEntity> findByProjectIdAndServiceIdAndFingerprint(
            String projectId, String serviceId, String fingerprint);

    List<LogErrorFingerprintEntity> findTop10ByProjectIdOrderByLastSeenDesc(String projectId);

    long deleteByLastSeenBefore(Instant cutoff);
}
