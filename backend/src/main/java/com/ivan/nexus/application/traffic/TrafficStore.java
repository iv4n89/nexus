package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrafficStore {
    Optional<TrafficMinuteBucket> findBucket(String projectId, String serviceId, String host, Instant bucketStart);

    TrafficMinuteBucket save(TrafficMinuteBucket bucket);

    List<TrafficMinuteBucket> findByProjectSince(String projectId, Instant since);

    List<TrafficMinuteBucket> findSince(Instant since);

    TrafficSnapshot snapshot(String projectId, Instant from, Instant to);

    int deleteOlderThan(Instant cutoff);
}
