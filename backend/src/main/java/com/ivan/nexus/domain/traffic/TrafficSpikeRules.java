package com.ivan.nexus.domain.traffic;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TrafficSpikeRules {

    public static final int MIN_VOLUME_REQUESTS = 30;
    public static final double VOLUME_MULTIPLIER = 3.0;
    public static final double VOLUME_RESOLVE_MULTIPLIER = 1.5;
    public static final int MIN_BASELINE_SAMPLES = 12;
    public static final int MIN_5XX_REQUESTS = 10;
    public static final double FIVE_XX_RATE = 0.05;
    public static final int FIVE_XX_ABSOLUTE = 10;
    public static final double FIVE_XX_RESOLVE_RATE = 0.02;

    private TrafficSpikeRules() {}

    public static boolean volumeSpike(long requests, List<Long> baseline) {
        return requests >= MIN_VOLUME_REQUESTS
                && baseline.size() >= MIN_BASELINE_SAMPLES
                && requests >= VOLUME_MULTIPLIER * median(baseline);
    }

    public static boolean volumeResolved(long requests, List<Long> baseline) {
        return requests < VOLUME_RESOLVE_MULTIPLIER * median(baseline);
    }

    public static boolean fiveXxSpike(long requests, long fiveXx) {
        return (requests >= MIN_5XX_REQUESTS && (double) fiveXx / requests >= FIVE_XX_RATE)
                || fiveXx >= FIVE_XX_ABSOLUTE;
    }

    public static boolean fiveXxResolved(long requests, long fiveXx) {
        return (double) fiveXx / requests < FIVE_XX_RESOLVE_RATE && fiveXx < FIVE_XX_ABSOLUTE;
    }

    public static boolean sameUtcMinuteOfDay(Instant a, Instant b) {
        OffsetDateTime da = a.atOffset(ZoneOffset.UTC);
        OffsetDateTime db = b.atOffset(ZoneOffset.UTC);
        return da.getHour() == db.getHour() && da.getMinute() == db.getMinute();
    }

    static double median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size == 0) {
            return 0.0;
        }
        int mid = size / 2;
        if (size % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        }
        return sorted.get(mid);
    }
}
