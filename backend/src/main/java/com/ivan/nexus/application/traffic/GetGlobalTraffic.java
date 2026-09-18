package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import com.ivan.nexus.domain.traffic.TrafficOverview;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class GetGlobalTraffic {
    private final TrafficStore store;
    private final Clock clock;

    public GetGlobalTraffic(TrafficStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TrafficOverview execute(int hours) {
        Duration binSize = GetProjectTraffic.binSizeFor(hours);
        Instant to = clock.instant();
        Instant from = to.minus(hours, ChronoUnit.HOURS);
        List<TrafficMinuteBucket> minutes = store.findSince(from).stream()
                .filter(bucket -> bucket.bucketStart().isBefore(to))
                .toList();
        return TrafficOverview.fromMinutes(from, to, minutes, binSize);
    }
}
