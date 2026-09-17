package com.ivan.nexus.domain.alert;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEvaluatorTest {

    private final AlertEvaluator evaluator = new AlertEvaluator();

    @Test
    void runningToExitedFiresContainerStopped() {
        AlertEvaluation result = evaluator.evaluate(facts(
                List.of(container("web-id", "running", "healthy", 0, null)),
                List.of(container("web-id", "exited", null, 0, null)),
                0));

        assertThat(result.firings()).containsExactly(
                firing(AlertType.CONTAINER_STOPPED, "lab", "web", "Container web is exited"));
        assertThat(result.resolveKeys()).doesNotContain(
                new AlertKey(AlertType.CONTAINER_STOPPED, "lab", "web"));
    }

    @Test
    void exitedStaysExitedStillReturnsFiring() {
        evaluator.evaluate(facts(
                List.of(container("web-id", "running", "healthy", 0, null)),
                List.of(container("web-id", "exited", null, 0, null)),
                0));

        AlertEvaluation result = evaluator.evaluate(facts(
                List.of(container("web-id", "exited", null, 0, null)),
                List.of(container("web-id", "exited", null, 0, null)),
                0));

        assertThat(result.firings()).anyMatch(firing -> firing.key().type() == AlertType.CONTAINER_STOPPED);
    }

    @Test
    void restartCountJumpOfThreeWithinFiveMinutesFiresRestartSpike() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2026-01-01T00:04:00Z");

        evaluator.evaluate(facts(t0, List.of(), List.of(container("web-id", "running", "healthy", 1, null)), 0));
        AlertEvaluation result = evaluator.evaluate(
                facts(t1, List.of(), List.of(container("web-id", "running", "healthy", 5, null)), 0));

        assertThat(result.firings()).anyMatch(firing -> firing.key().equals(
                new AlertKey(AlertType.RESTART_SPIKE, "lab", "web")));
    }

    @Test
    void memoryAboveThresholdFiresHighMemory() {
        AlertEvaluation result = evaluator.evaluate(facts(
                List.of(),
                List.of(container("web-id", "running", "healthy", 0, 95.0)),
                0));

        assertThat(result.firings()).anyMatch(firing -> firing.key().equals(
                new AlertKey(AlertType.HIGH_MEMORY, "lab", "web")));
    }

    @Test
    void memoryBelowThresholdDoesNotFireHighMemory() {
        AlertEvaluation result = evaluator.evaluate(facts(
                List.of(),
                List.of(container("web-id", "running", "healthy", 0, 50.0)),
                0));

        assertThat(result.firings()).noneMatch(firing -> firing.key().type() == AlertType.HIGH_MEMORY);
    }

    @Test
    void diskAboveThresholdFiresDisk() {
        AlertEvaluation result = evaluator.evaluate(facts(List.of(), List.of(), 90));

        assertThat(result.firings()).contains(
                firing(AlertType.DISK, null, null, "Host disk is 90%"));
    }

    @Test
    void unhealthyFiresDockerHealth() {
        AlertEvaluation result = evaluator.evaluate(facts(
                List.of(),
                List.of(container("web-id", "running", "unhealthy", 0, null)),
                0));

        assertThat(result.firings()).anyMatch(firing -> firing.key().equals(
                new AlertKey(AlertType.DOCKER_HEALTH, "lab", "web")));
    }

    @Test
    void healthyAfterUnhealthyIsResolveKey() {
        evaluator.evaluate(facts(
                List.of(),
                List.of(container("web-id", "running", "unhealthy", 0, null)),
                0));

        AlertEvaluation result = evaluator.evaluate(facts(
                List.of(),
                List.of(container("web-id", "running", "healthy", 0, null)),
                0));

        assertThat(result.firings()).noneMatch(firing -> firing.key().type() == AlertType.DOCKER_HEALTH);
        assertThat(result.resolveKeys()).contains(new AlertKey(AlertType.DOCKER_HEALTH, "lab", "web"));
    }

    @Test
    void elevenHitsInThirtySecondsDoNotFireErrorRateOfTenPerMinute() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        AlertEvaluation result = evaluator.evaluate(errorFacts(t0, 11, 10));

        assertThat(result.firings()).noneMatch(firing -> firing.key().type() == AlertType.ERROR_RATE);
    }

    @Test
    void elevenHitsSpanningSixtySecondsFireErrorRateOfTenPerMinute() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t1 = Instant.parse("2026-01-01T00:01:00Z");
        evaluator.evaluate(errorFacts(t0, 5, 10));
        AlertEvaluation result = evaluator.evaluate(errorFacts(t1, 6, 10));

        assertThat(result.firings()).anyMatch(firing -> firing.key().equals(
                new AlertKey(AlertType.ERROR_RATE, "lab", "api")));
    }

    private static AlertFiring firing(AlertType type, String projectId, String serviceId, String message) {
        return new AlertFiring(new AlertKey(type, projectId, serviceId), message);
    }

    private static AlertFacts facts(
            List<ContainerAlertState> previous,
            List<ContainerAlertState> current,
            double diskPercent) {
        return facts(Instant.parse("2026-01-01T00:00:00Z"), previous, current, diskPercent);
    }

    private static AlertFacts facts(
            Instant now,
            List<ContainerAlertState> previous,
            List<ContainerAlertState> current,
            double diskPercent) {
        return new AlertFacts(now, previous, current, diskPercent, 85, List.of(), List.of());
    }

    private static AlertFacts errorFacts(Instant now, int newHits, int threshold) {
        return new AlertFacts(
                now,
                List.of(),
                List.of(),
                0,
                85,
                List.of(new ErrorRateState("lab", "api", newHits, threshold)),
                List.of());
    }

    private static ContainerAlertState container(
            String id, String state, String health, int restartCount, Double memoryPercent) {
        return new ContainerAlertState(id, "lab", "web", state, health, restartCount, memoryPercent, 90);
    }
}
