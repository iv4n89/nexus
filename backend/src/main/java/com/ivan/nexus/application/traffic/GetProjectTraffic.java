package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import com.ivan.nexus.domain.traffic.TrafficReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        Duration binSize = binSizeFor(hours);
        Instant to = clock.instant();
        Instant from = to.minus(hours, ChronoUnit.HOURS);
        List<TrafficMinuteBucket> minutes = store.findByProjectSince(projectId, from).stream()
                .filter(bucket -> bucket.bucketStart().isBefore(to))
                .toList();
        return TrafficReport.fromMinutes(projectId, from, to, minutes, binSize);
    }

    static Duration binSizeFor(int hours) {
        if (hours < 1 || hours > 168) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "hours must be between 1 and 168");
        }
        return hours <= 24 ? Duration.ofMinutes(1) : Duration.ofMinutes(15);
    }
}
