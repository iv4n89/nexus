package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.traffic.TrafficSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class GetProjectTraffic {
    private final TrafficStore store;
    private final Clock clock;

    public GetProjectTraffic(TrafficStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TrafficSnapshot execute(String projectId, int hours) {
        int window = Math.max(1, Math.min(hours, 168));
        Instant to = clock.instant();
        Instant from = to.minus(window, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        return store.snapshot(projectId, from, to);
    }
}
