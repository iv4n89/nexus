package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.traffic.TrafficHourlyBucket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class HourlyTrafficIngestor implements TrafficIngestor {
    private final TrafficStore store;
    private final Clock clock;

    public HourlyTrafficIngestor(TrafficStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void ingestRaw(String projectId, String domainId, int status, long bytes, long latencyMs) {
        Instant bucketStart = clock.instant().truncatedTo(ChronoUnit.HOURS);
        String normalizedDomain = TrafficHourlyBucket.normalizeDomainId(domainId);
        TrafficHourlyBucket bucket = store.findBucket(projectId, normalizedDomain, bucketStart)
                .orElseGet(() -> TrafficHourlyBucket.empty(
                        UUID.randomUUID(), projectId, normalizedDomain, bucketStart));
        store.save(bucket.ingest(status, bytes, latencyMs));
    }
}
