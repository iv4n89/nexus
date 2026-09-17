package com.ivan.nexus.application.log;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FingerprintStore {
    Optional<StoredErrorFingerprint> findByProjectIdAndServiceIdAndFingerprint(
            String projectId, String serviceId, String fingerprint);

    StoredErrorFingerprint save(StoredErrorFingerprint fingerprint);

    List<StoredErrorFingerprint> findTop10ByProjectIdAndLastSeenAfterOrderByLastSeenDesc(
            String projectId, Instant cutoff);

    List<StoredErrorFingerprint> findTop50ByProjectIdAndLastSeenAfterOrderByLastSeenDesc(
            String projectId, Instant cutoff);

    List<StoredErrorFingerprint> findTop50ByProjectIdAndServiceIdAndLastSeenAfterOrderByLastSeenDesc(
            String projectId, String serviceId, Instant cutoff);

    List<StoredErrorFingerprint> findAll();

    long deleteByLastSeenBefore(Instant cutoff);
}
