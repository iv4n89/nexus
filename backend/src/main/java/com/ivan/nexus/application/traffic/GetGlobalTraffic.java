package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import com.ivan.nexus.domain.traffic.TrafficOverview;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
        TrafficHours.Window window = TrafficHours.window(clock, hours);
        List<TrafficMinuteBucket> minutes = store.findSince(window.from()).stream()
                .filter(bucket -> bucket.bucketStart().isBefore(window.to()))
                .toList();
        return TrafficOverview.fromMinutes(window.from(), window.to(), minutes, window.binSize());
    }
}
