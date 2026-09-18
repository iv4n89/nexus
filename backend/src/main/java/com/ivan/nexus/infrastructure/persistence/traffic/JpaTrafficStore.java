package com.ivan.nexus.infrastructure.persistence.traffic;

import com.ivan.nexus.application.traffic.TrafficStore;
import com.ivan.nexus.domain.traffic.TrafficHourlyBucket;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class JpaTrafficStore implements TrafficStore {
    private final TrafficHourlyJpaRepository repository;

    public JpaTrafficStore(TrafficHourlyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TrafficHourlyBucket> findBucket(String projectId, String domainId, Instant bucketStart) {
        return repository
                .findByProjectIdAndDomainIdAndBucketStart(
                        projectId, TrafficHourlyBucket.normalizeDomainId(domainId), bucketStart)
                .map(TrafficHourlyEntity::toDomain);
    }

    @Override
    public TrafficHourlyBucket save(TrafficHourlyBucket bucket) {
        Optional<TrafficHourlyEntity> existing = repository.findById(bucket.id());
        if (existing.isPresent()) {
            TrafficHourlyEntity entity = existing.get();
            entity.apply(bucket);
            return repository.save(entity).toDomain();
        }
        return repository.save(new TrafficHourlyEntity(bucket)).toDomain();
    }

    @Override
    public List<TrafficHourlyBucket> findByProjectSince(String projectId, Instant since) {
        return repository
                .findByProjectIdAndBucketStartGreaterThanEqualOrderByBucketStartAsc(projectId, since)
                .stream()
                .map(TrafficHourlyEntity::toDomain)
                .toList();
    }

    @Override
    public TrafficSnapshot snapshot(String projectId, Instant from, Instant to) {
        return TrafficSnapshot.aggregate(projectId, from, to, findByProjectSince(projectId, from));
    }
}
