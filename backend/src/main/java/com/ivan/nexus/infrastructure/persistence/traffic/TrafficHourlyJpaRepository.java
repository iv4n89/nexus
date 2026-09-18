package com.ivan.nexus.infrastructure.persistence.traffic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TrafficHourlyJpaRepository extends JpaRepository<TrafficHourlyEntity, UUID> {
    Optional<TrafficHourlyEntity> findByProjectIdAndDomainIdAndBucketStart(
            String projectId, String domainId, Instant bucketStart);

    List<TrafficHourlyEntity> findByProjectIdAndBucketStartGreaterThanEqualOrderByBucketStartAsc(
            String projectId, Instant since);
}
