package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class MinuteTrafficIngestor implements TrafficIngestor {
    private final TrafficStore store;
    private final Clock clock;

    public MinuteTrafficIngestor(TrafficStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void ingestRaw(String projectId, String serviceId, String host, int status, long bytes, long latencyMs) {
        Instant bucketStart = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        String service = TrafficMinuteBucket.normalizeServiceId(serviceId);
        String normalizedHost = TrafficMinuteBucket.normalizeHost(host);
        TrafficMinuteBucket bucket = store.findBucket(projectId, service, normalizedHost, bucketStart)
                .orElseGet(() -> TrafficMinuteBucket.empty(
                        UUID.randomUUID(), projectId, service, normalizedHost, bucketStart));
        store.save(bucket.ingest(status, bytes, latencyMs));
    }
}
