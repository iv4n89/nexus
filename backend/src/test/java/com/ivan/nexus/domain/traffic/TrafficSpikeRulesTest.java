package com.ivan.nexus.domain.traffic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficSpikeRulesTest {
    @Test
    void volumeFiresAtThreeTimesMedianWhenAtLeast30Requests() {
        assertThat(TrafficSpikeRules.volumeSpike(90, baselineOf(30, 7))).isTrue();
    }

    @Test
    void volumeDoesNotFireBelow30Requests() {
        assertThat(TrafficSpikeRules.volumeSpike(29, baselineOf(1, 12))).isFalse();
    }

    @Test
    void volumeDoesNotFireWithThinBaseline() {
        assertThat(TrafficSpikeRules.volumeSpike(90, baselineOf(30, 6))).isFalse();
    }

    @Test
    void volumeResolvesBelowOnePointFiveMedian() {
        assertThat(TrafficSpikeRules.volumeResolved(44, baselineOf(30, 12))).isTrue();
    }

    @Test
    void volumeResolvesBelowMinVolumeRequests() {
        assertThat(TrafficSpikeRules.volumeResolved(29, baselineOf(1, 12))).isTrue();
    }

    @Test
    void fiveXxFiresOnRateOrAbsolute() {
        assertThat(TrafficSpikeRules.fiveXxSpike(10, 1)).isTrue();
        assertThat(TrafficSpikeRules.fiveXxSpike(100, 10)).isTrue();
        assertThat(TrafficSpikeRules.fiveXxSpike(9, 0)).isFalse();
    }

    @Test
    void fiveXxResolvesUnderTwoPercentAndUnder10() {
        assertThat(TrafficSpikeRules.fiveXxResolved(100, 1)).isTrue();
        assertThat(TrafficSpikeRules.fiveXxResolved(100, 3)).isFalse();
    }

    @Test
    void fiveXxResolvedWithZeroRequests() {
        assertThat(TrafficSpikeRules.fiveXxResolved(0, 0)).isTrue();
        assertThat(TrafficSpikeRules.fiveXxResolved(0, 1)).isFalse();
    }

    private static List<Long> baselineOf(long value, int n) {
        List<Long> baseline = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            baseline.add(value);
        }
        return baseline;
    }
}
