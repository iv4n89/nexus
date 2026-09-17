package com.ivan.nexus.domain.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AlertEvaluator {
    public static final int DEFAULT_MEMORY_PERCENT = 90;
    public static final int DEFAULT_DISK_PERCENT = 85;
    public static final int DEFAULT_ERROR_RATE_PER_MINUTE = 10;
    static final int RESTART_SPIKE_DELTA = 3;
    static final Duration RESTART_SPIKE_WINDOW = Duration.ofMinutes(5);
    static final Duration ERROR_RATE_WINDOW = Duration.ofMinutes(1);

    private final Map<String, List<RestartSample>> restartHistory = new HashMap<>();
    private final Map<String, List<ErrorHitSample>> errorHistory = new HashMap<>();
    private final Set<String> stoppedContainers = new HashSet<>();

    public AlertEvaluation evaluate(AlertFacts facts) {
        List<AlertFiring> firings = new ArrayList<>();
        LinkedHashMap<AlertKey, AlertKey> resolveKeys = new LinkedHashMap<>();

        Map<String, ContainerAlertState> previousById = indexById(facts.previous());
        Map<String, ContainerAlertState> currentById = indexById(facts.current());
        Set<String> ids = new HashSet<>();
        ids.addAll(previousById.keySet());
        ids.addAll(currentById.keySet());

        for (String id : ids) {
            ContainerAlertState current = currentById.get(id);
            ContainerAlertState previous = previousById.get(id);
            evaluateContainer(facts.now(), previous, current, firings, resolveKeys);
        }

        evaluateDisk(facts, firings, resolveKeys);
        this.evaluateErrorRates(facts, firings, resolveKeys);
        evaluateHttpHealth(facts, firings, resolveKeys);

        return new AlertEvaluation(firings, List.copyOf(resolveKeys.keySet()));
    }

    private void evaluateContainer(
            Instant now,
            ContainerAlertState previous,
            ContainerAlertState current,
            List<AlertFiring> firings,
            Map<AlertKey, AlertKey> resolveKeys) {
        if (current == null) {
            if (previous != null) {
                resolve(resolveKeys, AlertType.CONTAINER_STOPPED, previous);
                resolve(resolveKeys, AlertType.RESTART_SPIKE, previous);
                resolve(resolveKeys, AlertType.HIGH_MEMORY, previous);
                resolve(resolveKeys, AlertType.DOCKER_HEALTH, previous);
                stoppedContainers.remove(previous.containerId());
                restartHistory.remove(previous.containerId());
            }
            return;
        }

        evaluateStopped(previous, current, firings, resolveKeys);
        evaluateRestartSpike(now, current, firings, resolveKeys);
        evaluateMemory(current, firings, resolveKeys);
        evaluateDockerHealth(current, firings, resolveKeys);
    }

    private void evaluateStopped(
            ContainerAlertState previous,
            ContainerAlertState current,
            List<AlertFiring> firings,
            Map<AlertKey, AlertKey> resolveKeys) {
        AlertKey key = key(AlertType.CONTAINER_STOPPED, current);
        if (isStopped(current.state())) {
            if (previous != null && isRunning(previous.state())) {
                stoppedContainers.add(current.containerId());
            }
            if (stoppedContainers.contains(current.containerId())) {
                fire(firings, key, "Container " + current.serviceId() + " is " + current.state());
            }
            return;
        }
        stoppedContainers.remove(current.containerId());
        resolveKeys.put(key, key);
    }

    private void evaluateRestartSpike(
            Instant now,
            ContainerAlertState current,
            List<AlertFiring> firings,
            Map<AlertKey, AlertKey> resolveKeys) {
        AlertKey key = key(AlertType.RESTART_SPIKE, current);
        List<RestartSample> samples = restartHistory.computeIfAbsent(current.containerId(), id -> new ArrayList<>());
        samples.add(new RestartSample(now, current.restartCount()));
        Instant cutoff = now.minus(RESTART_SPIKE_WINDOW);
        samples.removeIf(sample -> sample.at().isBefore(cutoff));
        int oldest = samples.getFirst().restartCount();
        if (current.restartCount() - oldest >= RESTART_SPIKE_DELTA) {
            fire(firings, key, "Container " + current.serviceId() + " restarted "
                    + (current.restartCount() - oldest) + " times in 5 minutes");
            return;
        }
        resolveKeys.put(key, key);
    }

    private void evaluateMemory(
            ContainerAlertState current,
            List<AlertFiring> firings,
            Map<AlertKey, AlertKey> resolveKeys) {
        AlertKey key = key(AlertType.HIGH_MEMORY, current);
        if (current.memoryPercent() != null && current.memoryPercent() > current.memoryThreshold()) {
            fire(firings, key, "Container " + current.serviceId() + " memory is "
                    + formatPercent(current.memoryPercent()) + "%");
            return;
        }
        resolveKeys.put(key, key);
    }

    private void evaluateDockerHealth(
            ContainerAlertState current,
            List<AlertFiring> firings,
            Map<AlertKey, AlertKey> resolveKeys) {
        AlertKey key = key(AlertType.DOCKER_HEALTH, current);
        if (current.health() != null && current.health().equalsIgnoreCase("unhealthy")) {
            fire(firings, key, "Container " + current.serviceId() + " is unhealthy");
            return;
        }
        resolveKeys.put(key, key);
    }

    private static void evaluateDisk(
            AlertFacts facts,
            List<AlertFiring> firings,
            Map<AlertKey, AlertKey> resolveKeys) {
        AlertKey key = new AlertKey(AlertType.DISK, null, null);
        if (facts.diskPercent() > facts.diskThreshold()) {
            fire(firings, key, "Host disk is " + formatPercent(facts.diskPercent()) + "%");
            return;
        }
        resolveKeys.put(key, key);
    }

    private void evaluateErrorRates(
            AlertFacts facts,
            List<AlertFiring> firings,
            Map<AlertKey, AlertKey> resolveKeys) {
        for (ErrorRateState rate : facts.errorRates()) {
            AlertKey key = new AlertKey(AlertType.ERROR_RATE, rate.projectId(), rate.serviceId());
            String historyKey = rate.projectId() + "\0" + rate.serviceId();
            List<ErrorHitSample> samples = errorHistory.computeIfAbsent(historyKey, ignored -> new ArrayList<>());
            samples.add(new ErrorHitSample(facts.now(), rate.newHits()));
            Instant cutoff = facts.now().minus(ERROR_RATE_WINDOW);
            samples.removeIf(sample -> sample.at().isBefore(cutoff));
            int sum = 0;
            for (ErrorHitSample sample : samples) {
                sum += sample.hits();
            }
            boolean spanned = !samples.isEmpty() && !samples.getFirst().at().isAfter(cutoff);
            if (spanned && sum > rate.threshold()) {
                fire(firings, key, "Error rate is " + sum + " hits per minute");
            } else {
                resolveKeys.put(key, key);
            }
        }
    }

    private static void evaluateHttpHealth(
            AlertFacts facts,
            List<AlertFiring> firings,
            Map<AlertKey, AlertKey> resolveKeys) {
        for (HttpHealthState check : facts.httpHealthChecks()) {
            AlertKey key = new AlertKey(AlertType.HTTP_HEALTH, check.projectId(), null);
            if (!check.healthy()) {
                fire(firings, key, "HTTP health check failed for " + check.projectId());
            } else {
                resolveKeys.put(key, key);
            }
        }
    }

    private static void fire(List<AlertFiring> firings, AlertKey key, String message) {
        firings.add(new AlertFiring(key, message));
    }

    private static void resolve(Map<AlertKey, AlertKey> resolveKeys, AlertType type, ContainerAlertState state) {
        AlertKey key = key(type, state);
        resolveKeys.put(key, key);
    }

    private static AlertKey key(AlertType type, ContainerAlertState state) {
        return new AlertKey(type, state.projectId(), state.serviceId());
    }

    private static Map<String, ContainerAlertState> indexById(List<ContainerAlertState> states) {
        Map<String, ContainerAlertState> indexed = new LinkedHashMap<>();
        for (ContainerAlertState state : states) {
            indexed.put(state.containerId(), state);
        }
        return indexed;
    }

    private static boolean isRunning(String state) {
        return state != null && state.equalsIgnoreCase("running");
    }

    private static boolean isStopped(String state) {
        if (state == null) {
            return false;
        }
        return state.equalsIgnoreCase("exited")
                || state.equalsIgnoreCase("dead")
                || state.equalsIgnoreCase("stopped");
    }

    private static String formatPercent(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private record RestartSample(Instant at, int restartCount) {
    }

    private record ErrorHitSample(Instant at, int hits) {
    }
}
