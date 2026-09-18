package com.ivan.nexus.application.traffic;

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
    private final Clock clock;

    public EnforceTrafficRetention(TrafficStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(cron = "${nexus.traffic.retention-cron:0 15 3 * * *}")
    public void execute() {
        store.deleteOlderThan(clock.instant().minus(7, ChronoUnit.DAYS));
    }
}
