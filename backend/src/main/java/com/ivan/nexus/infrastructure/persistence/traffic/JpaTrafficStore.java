package com.ivan.nexus.infrastructure.persistence.traffic;

import com.ivan.nexus.application.traffic.TrafficStore;
import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class JpaTrafficStore implements TrafficStore {
    private final TrafficMinuteJpaRepository repository;

    public JpaTrafficStore(TrafficMinuteJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TrafficMinuteBucket> findBucket(
            String projectId, String serviceId, String host, Instant bucketStart) {
        return repository
                .findByProjectIdAndServiceIdAndHostAndBucketStart(
                        projectId,
                        TrafficMinuteBucket.normalizeServiceId(serviceId),
                        TrafficMinuteBucket.normalizeHost(host),
                        bucketStart)
                .map(TrafficMinuteEntity::toDomain);
    }

    @Override
    public TrafficMinuteBucket save(TrafficMinuteBucket bucket) {
        Optional<TrafficMinuteEntity> existing = repository.findById(bucket.id());
        if (existing.isPresent()) {
            TrafficMinuteEntity entity = existing.get();
            entity.apply(bucket);
            return repository.save(entity).toDomain();
        }
        return repository.save(new TrafficMinuteEntity(bucket)).toDomain();
    }

    @Override
    public List<TrafficMinuteBucket> findByProjectSince(String projectId, Instant since) {
        return repository
                .findByProjectIdAndBucketStartGreaterThanEqualOrderByBucketStartAsc(projectId, since)
                .stream()
                .map(TrafficMinuteEntity::toDomain)
                .toList();
    }

    @Override
    public List<TrafficMinuteBucket> findSince(Instant since) {
        return repository.findByBucketStartGreaterThanEqualOrderByBucketStartAsc(since).stream()
                .map(TrafficMinuteEntity::toDomain)
                .toList();
    }

    @Override
    public TrafficSnapshot snapshot(String projectId, Instant from, Instant to) {
        List<TrafficMinuteBucket> buckets = findByProjectSince(projectId, from).stream()
                .filter(bucket -> bucket.bucketStart().isBefore(to))
                .toList();
        return TrafficSnapshot.aggregateMinutes(projectId, from, to, buckets);
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return repository.deleteByBucketStartBefore(cutoff);
    }
}
