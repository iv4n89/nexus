package com.ivan.nexus.application.traffic;

import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Transactional
public class EnforceTrafficRetention {
    private final TrafficStore store;
    private final NexusProperties properties;
    private final Clock clock;

    public EnforceTrafficRetention(TrafficStore store, NexusProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${nexus.traffic.retention-cron:0 15 3 * * *}")
    public void execute() {
        Instant cutoff = clock.instant().minus(properties.getTraffic().getRetentionDays(), ChronoUnit.DAYS);
        store.deleteOlderThan(cutoff);
    }
}
