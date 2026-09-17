package com.ivan.nexus.application.deployment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentSummaryTest {

    @Test
    void joinsLinesUnderLimit() {
        assertThat(DeploymentSummary.summarize(List.of("deployment started", "ok", "DEPLOYMENT SUCCESS")))
                .isEqualTo("deployment started\nok\nDEPLOYMENT SUCCESS");
    }

    @Test
    void keepsTheTrailingLimitCharacters() {
        String head = "x".repeat(20);
        String tail = "y".repeat(DeploymentSummary.SUMMARY_LIMIT);
        String summarized = DeploymentSummary.summarize(List.of(head, tail));
        assertThat(summarized).hasSize(DeploymentSummary.SUMMARY_LIMIT);
        assertThat(summarized).isEqualTo(tail);
    }
}
