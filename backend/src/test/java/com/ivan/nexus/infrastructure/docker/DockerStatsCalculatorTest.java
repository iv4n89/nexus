package com.ivan.nexus.infrastructure.docker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DockerStatsCalculatorTest {

    @Test
    void cpuPercentUsesDockerDeltaFormula() {
        // cpuDelta=100, systemDelta=200, ncpu=2 → (100/200)*2*100 = 100
        assertThat(DockerStatsCalculator.cpuPercent(200L, 100L, 400L, 200L, 2L, null))
                .isCloseTo(100.0, within(0.0001));
    }

    @Test
    void cpuPercentIsZeroWhenSystemDeltaIsNotPositive() {
        assertThat(DockerStatsCalculator.cpuPercent(200L, 100L, 100L, 100L, 1L, null)).isZero();
        assertThat(DockerStatsCalculator.cpuPercent(200L, 100L, 50L, 100L, 1L, null)).isZero();
    }

    @Test
    void cpuPercentIsZeroWhenCpuDeltaIsNegative() {
        assertThat(DockerStatsCalculator.cpuPercent(50L, 100L, 400L, 200L, 1L, null)).isZero();
    }

    @Test
    void cpuPercentFallsBackToPercpuCountThenOne() {
        // cpuDelta=100, systemDelta=100, ncpu=4 → 400
        assertThat(DockerStatsCalculator.cpuPercent(200L, 100L, 200L, 100L, null, 4))
                .isCloseTo(400.0, within(0.0001));
        // ncpu=1
        assertThat(DockerStatsCalculator.cpuPercent(200L, 100L, 200L, 100L, null, null))
                .isCloseTo(100.0, within(0.0001));
    }

    @Test
    void cpuPercentTreatsNullTotalsAsZero() {
        assertThat(DockerStatsCalculator.cpuPercent(null, null, null, null, 2L, 4)).isZero();
    }
}
