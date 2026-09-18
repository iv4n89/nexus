package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.traffic.TrafficHourlyBucket;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrafficStore {
    Optional<TrafficHourlyBucket> findBucket(String projectId, String domainId, Instant bucketStart);

    TrafficHourlyBucket save(TrafficHourlyBucket bucket);

    List<TrafficHourlyBucket> findByProjectSince(String projectId, Instant since);

    TrafficSnapshot snapshot(String projectId, Instant from, Instant to);
}
