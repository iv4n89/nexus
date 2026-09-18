package com.ivan.nexus.application.traffic;

import com.ivan.nexus.application.alert.AlertRuleStore;
import com.ivan.nexus.application.alert.PersistAlertEvaluation;
import com.ivan.nexus.domain.alert.AlertEvaluation;
import com.ivan.nexus.domain.alert.AlertFiring;
import com.ivan.nexus.domain.alert.AlertKey;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import com.ivan.nexus.domain.traffic.TrafficSpikeRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class EvaluateTrafficAlerts {
    private static final Logger log = LoggerFactory.getLogger(EvaluateTrafficAlerts.class);

    private final TrafficStore store;
    private final AlertRuleStore rules;
    private final PersistAlertEvaluation persistAlertEvaluation;
    private final Clock clock;

    public EvaluateTrafficAlerts(
            TrafficStore store,
            AlertRuleStore rules,
            PersistAlertEvaluation persistAlertEvaluation,
            Clock clock) {
        this.store = store;
        this.rules = rules;
        this.persistAlertEvaluation = persistAlertEvaluation;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${nexus.traffic.alert-interval-ms:60000}")
    public void execute() {
        Instant now = clock.instant();
        Instant closed = now.truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.MINUTES);
        List<TrafficMinuteBucket> buckets = store.findSince(closed.minus(7, ChronoUnit.DAYS));

        Map<String, List<TrafficMinuteBucket>> byProject = new LinkedHashMap<>();
        for (TrafficMinuteBucket bucket : buckets) {
            if (bucket.projectId() == null) {
                continue;
            }
            byProject.computeIfAbsent(bucket.projectId(), id -> new ArrayList<>()).add(bucket);
        }

        List<AlertFiring> firings = new ArrayList<>();
        List<AlertKey> resolveKeys = new ArrayList<>();
        for (Map.Entry<String, List<TrafficMinuteBucket>> entry : byProject.entrySet()) {
            try {
                evaluateProject(entry.getKey(), entry.getValue(), closed, firings, resolveKeys);
            } catch (RuntimeException ex) {
                log.warn("Traffic alert evaluation failed for project {}", entry.getKey(), ex);
            }
        }
        persistAlertEvaluation.persist(new AlertEvaluation(firings, resolveKeys), rules.findEnabled(), now);
    }

    private static void evaluateProject(
            String projectId,
            List<TrafficMinuteBucket> buckets,
            Instant closed,
            List<AlertFiring> firings,
            List<AlertKey> resolveKeys) {
        long closedRequests = 0;
        Map<Instant, Long> baselineByStart = new LinkedHashMap<>();
        Map<String, long[]> closedByService = new LinkedHashMap<>();
        Set<String> serviceIds = new LinkedHashSet<>();

        for (TrafficMinuteBucket bucket : buckets) {
            Instant start = bucket.bucketStart();
            serviceIds.add(bucket.serviceId());
            if (closed.equals(start)) {
                closedRequests += bucket.requests();
                long[] stats = closedByService.computeIfAbsent(bucket.serviceId(), id -> new long[2]);
                stats[0] += bucket.requests();
                stats[1] += bucket.status5xx();
            } else if (TrafficSpikeRules.sameUtcMinuteOfDay(closed, start)) {
                baselineByStart.merge(start, bucket.requests(), Long::sum);
            }
        }

        AlertKey volumeKey = new AlertKey(AlertType.TRAFFIC_SPIKE, projectId, null);
        List<Long> baseline = new ArrayList<>(baselineByStart.values());
        if (TrafficSpikeRules.volumeSpike(closedRequests, baseline)) {
            double median = median(baseline);
            double multiple = median == 0.0 ? 0.0 : closedRequests / median;
            firings.add(new AlertFiring(
                    volumeKey,
                    String.format(
                            Locale.ROOT,
                            "Traffic volume spike for %s: %d requests (%.1f× median of %.0f)",
                            projectId,
                            closedRequests,
                            multiple,
                            median)));
        } else if (TrafficSpikeRules.volumeResolved(closedRequests, baseline)) {
            resolveKeys.add(volumeKey);
        }

        for (String serviceId : serviceIds) {
            long[] stats = closedByService.getOrDefault(serviceId, new long[] {0, 0});
            long requests = stats[0];
            long fiveXx = stats[1];
            AlertKey fiveXxKey = new AlertKey(AlertType.TRAFFIC_5XX_SPIKE, projectId, serviceId);
            if (TrafficSpikeRules.fiveXxSpike(requests, fiveXx)) {
                long percent = requests == 0 ? 0 : Math.round(100.0 * fiveXx / requests);
                firings.add(new AlertFiring(
                        fiveXxKey,
                        String.format(
                                Locale.ROOT,
                                "5xx spike for %s/%s: %d/%d (%d%%)",
                                projectId,
                                serviceId,
                                fiveXx,
                                requests,
                                percent)));
            } else if (TrafficSpikeRules.fiveXxResolved(requests, fiveXx)) {
                resolveKeys.add(fiveXxKey);
            }
        }
    }

    private static double median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(null);
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
