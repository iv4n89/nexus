package com.ivan.nexus.infrastructure.persistence.traffic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TrafficMinuteJpaRepository extends JpaRepository<TrafficMinuteEntity, UUID> {
    Optional<TrafficMinuteEntity> findByProjectIdAndServiceIdAndHostAndBucketStart(
            String projectId, String serviceId, String host, Instant bucketStart);

    List<TrafficMinuteEntity> findByProjectIdAndBucketStartGreaterThanEqualOrderByBucketStartAsc(
            String projectId, Instant since);

    List<TrafficMinuteEntity> findByBucketStartGreaterThanEqualOrderByBucketStartAsc(Instant since);

    int deleteByBucketStartBefore(Instant cutoff);
}
