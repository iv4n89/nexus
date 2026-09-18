package com.ivan.nexus.domain.traffic;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record TrafficSeriesPoint(
        Instant t,
        long requests,
        long bytesOut,
        long status5xx,
        double latencyAvgMs,
        Double latencyMaxMs) {

    public static List<TrafficSeriesPoint> bin(List<TrafficMinuteBucket> minutes, Duration binSize) {
        long binSeconds = binSize.getSeconds();
        Map<Long, BinAccumulator> bins = new HashMap<>();
        for (TrafficMinuteBucket bucket : minutes) {
            long binStart = (bucket.bucketStart().getEpochSecond() / binSeconds) * binSeconds;
            bins.computeIfAbsent(binStart, BinAccumulator::new).add(bucket);
        }
        List<TrafficSeriesPoint> series = new ArrayList<>();
        for (BinAccumulator accumulator : bins.values()) {
            if (accumulator.requests == 0) {
                continue;
            }
            series.add(accumulator.toPoint());
        }
        series.sort(Comparator.comparing(TrafficSeriesPoint::t));
        return List.copyOf(series);
    }

    private static final class BinAccumulator {
        private final long binStart;
        private long requests;
        private long bytesOut;
        private long status5xx;
        private double latencyWeighted;
        private Double latencyMax;

        private BinAccumulator(long binStart) {
            this.binStart = binStart;
        }

        private void add(TrafficMinuteBucket bucket) {
            requests += bucket.requests();
            bytesOut += bucket.bytesOut();
            status5xx += bucket.status5xx();
            latencyWeighted += bucket.latencyAvgMs() * bucket.requests();
            if (bucket.latencyMaxMs() != null) {
                latencyMax = latencyMax == null
                        ? bucket.latencyMaxMs()
                        : Math.max(latencyMax, bucket.latencyMaxMs());
            }
        }

        private TrafficSeriesPoint toPoint() {
            double latencyAvg = requests == 0 ? 0.0 : latencyWeighted / requests;
            return new TrafficSeriesPoint(
                    Instant.ofEpochSecond(binStart),
                    requests,
                    bytesOut,
                    status5xx,
                    latencyAvg,
                    latencyMax);
        }
    }
}
