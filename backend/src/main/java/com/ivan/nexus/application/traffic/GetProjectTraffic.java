package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import com.ivan.nexus.domain.traffic.TrafficReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class GetProjectTraffic {
    private final TrafficStore store;
    private final Clock clock;

    public GetProjectTraffic(TrafficStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TrafficReport execute(String projectId, int hours) {
        TrafficHours.Window window = TrafficHours.window(clock, hours);
        List<TrafficMinuteBucket> minutes = store.findByProjectSince(projectId, window.from()).stream()
                .filter(bucket -> bucket.bucketStart().isBefore(window.to()))
                .toList();
        return TrafficReport.fromMinutes(projectId, window.from(), window.to(), minutes, window.binSize());
    }
}
