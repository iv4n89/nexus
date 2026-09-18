package com.ivan.nexus.application.traffic;

import com.ivan.nexus.application.alert.AlertRuleStore;
import com.ivan.nexus.application.alert.PersistAlertEvaluation;
import com.ivan.nexus.domain.alert.AlertEvaluation;
import com.ivan.nexus.domain.alert.AlertFiring;
import com.ivan.nexus.domain.alert.AlertKey;
import com.ivan.nexus.domain.alert.AlertRule;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EvaluateTrafficAlertsTest {

    private static final Instant NOW = Instant.parse("2026-09-18T10:32:10Z");
    private static final Instant CLOSED = Instant.parse("2026-09-18T10:31:00Z");

    @Mock
    AlertRuleStore rules;
    @Mock
    PersistAlertEvaluation persist;

    private FakeTrafficStore store;
    private List<AlertRule> enabledRules;
    private EvaluateTrafficAlerts evaluate;

    @BeforeEach
    void setUp() {
        store = new FakeTrafficStore();
        enabledRules = List.of(rule(AlertType.TRAFFIC_SPIKE), rule(AlertType.TRAFFIC_5XX_SPIKE));
        lenient().when(rules.findEnabled()).thenReturn(enabledRules);
        evaluate = new EvaluateTrafficAlerts(
                store, rules, persist, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void volumeSpikeFiresWhenClosedMinuteIsThreeTimesMedian() {
        addBaseline("lab", 12, 20, 10);
        store.save(minute("lab", "web", "a.example", CLOSED, 50, 0));
        store.save(minute("lab", "api", "b.example", CLOSED, 40, 0));

        evaluate.execute();

        AlertEvaluation evaluation = capturedEvaluation();
        AlertFiring firing = volumeFiring(evaluation);
        assertThat(firing.key()).isEqualTo(new AlertKey(AlertType.TRAFFIC_SPIKE, "lab", null));
        assertThat(firing.message()).isEqualTo("Traffic volume spike for lab: 90 requests (3.0× median of 30)");
        assertThat(evaluation.resolveKeys()).doesNotContain(firing.key());
    }

    @Test
    void volumeDoesNotFireBelow30Requests() {
        addBaseline("lab", 12, 30, 0);
        store.save(minute("lab", "web", "", CLOSED, 29, 0));

        evaluate.execute();

        AlertEvaluation evaluation = capturedEvaluation();
        assertThat(evaluation.firings()).noneMatch(firing -> firing.key().type() == AlertType.TRAFFIC_SPIKE);
    }

    @Test
    void thinBaselineDoesNotFire() {
        addBaseline("lab", 11, 30, 0);
        store.save(minute("lab", "web", "", CLOSED, 90, 0));

        evaluate.execute();

        AlertEvaluation evaluation = capturedEvaluation();
        assertThat(evaluation.firings()).noneMatch(firing -> firing.key().type() == AlertType.TRAFFIC_SPIKE);
        assertThat(evaluation.resolveKeys()).doesNotContain(new AlertKey(AlertType.TRAFFIC_SPIKE, "lab", null));
    }

    @Test
    void fiveXxSpikeFiresPerService() {
        store.save(minute("lab", "web", "a.example", CLOSED, 50, 5));
        store.save(minute("lab", "web", "b.example", CLOSED, 50, 5));
        store.save(minute("lab", "api", "", CLOSED, 100, 0));

        evaluate.execute();

        AlertEvaluation evaluation = capturedEvaluation();
        AlertKey web = new AlertKey(AlertType.TRAFFIC_5XX_SPIKE, "lab", "web");
        AlertKey api = new AlertKey(AlertType.TRAFFIC_5XX_SPIKE, "lab", "api");
        AlertFiring firing = evaluation.firings().stream()
                .filter(item -> item.key().equals(web))
                .findFirst()
                .orElseThrow();
        assertThat(firing.message()).isEqualTo("5xx spike for lab/web: 10/100 (10%)");
        assertThat(evaluation.firings()).noneMatch(item -> item.key().equals(api));
        assertThat(evaluation.resolveKeys()).contains(api);
        assertThat(evaluation.resolveKeys()).doesNotContain(web);
    }

    @Test
    void resolvesVolumeWhenRequestsDropBelowThreshold() {
        addBaseline("lab", 12, 30, 0);
        store.save(minute("lab", "web", "", CLOSED, 20, 0));

        evaluate.execute();

        AlertEvaluation evaluation = capturedEvaluation();
        AlertKey key = new AlertKey(AlertType.TRAFFIC_SPIKE, "lab", null);
        assertThat(evaluation.resolveKeys()).contains(key);
        assertThat(evaluation.firings()).noneMatch(firing -> firing.key().equals(key));
    }

    @Test
    void projectComputeFailureDoesNotSkipOthers() {
        addBaseline("shop", 12, 30, 0);
        store.save(minute("shop", "web", "", CLOSED, 90, 0));
        store.save(new TrafficMinuteBucket(
                UUID.randomUUID(),
                "broken",
                "web",
                "",
                null,
                1,
                0,
                0,
                1,
                0,
                0,
                0,
                0.0,
                null));

        assertThatCode(evaluate::execute).doesNotThrowAnyException();

        AlertEvaluation evaluation = capturedEvaluation();
        assertThat(evaluation.firings())
                .extracting(AlertFiring::key)
                .contains(new AlertKey(AlertType.TRAFFIC_SPIKE, "shop", null))
                .doesNotContain(new AlertKey(AlertType.TRAFFIC_SPIKE, "broken", null));
    }

    @Test
    void scheduledOnConfiguredInterval() throws Exception {
        Scheduled scheduled = EvaluateTrafficAlerts.class.getMethod("execute").getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${nexus.traffic.alert-interval-ms:60000}");
    }

    private AlertEvaluation capturedEvaluation() {
        ArgumentCaptor<AlertEvaluation> captor = ArgumentCaptor.forClass(AlertEvaluation.class);
        verify(persist).persist(captor.capture(), eq(enabledRules), eq(NOW));
        return captor.getValue();
    }

    private static AlertFiring volumeFiring(AlertEvaluation evaluation) {
        return evaluation.firings().stream()
                .filter(firing -> firing.key().type() == AlertType.TRAFFIC_SPIKE)
                .findFirst()
                .orElseThrow();
    }

    private void addBaseline(String projectId, int days, long firstHostRequests, long secondHostRequests) {
        for (int i = 1; i <= days; i++) {
            Instant ts = CLOSED.minus(i, ChronoUnit.DAYS);
            store.save(minute(projectId, "web", "a.example", ts, firstHostRequests, 0));
            if (secondHostRequests > 0) {
                store.save(minute(projectId, "api", "b.example", ts, secondHostRequests, 0));
            }
        }
    }

    private static TrafficMinuteBucket minute(
            String projectId, String serviceId, String host, Instant bucketStart, long requests, long status5xx) {
        long status2xx = requests - status5xx;
        return new TrafficMinuteBucket(
                UUID.randomUUID(),
                projectId,
                serviceId,
                host,
                bucketStart,
                requests,
                0,
                0,
                status2xx,
                0,
                0,
                status5xx,
                0.0,
                null);
    }

    private static AlertRule rule(AlertType type) {
        return new AlertRule(UUID.randomUUID(), null, type, Map.of(), true);
    }

    private static final class FakeTrafficStore implements TrafficStore {
        private final List<TrafficMinuteBucket> buckets = new ArrayList<>();

        @Override
        public Optional<TrafficMinuteBucket> findBucket(
                String projectId, String serviceId, String host, Instant bucketStart) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TrafficMinuteBucket save(TrafficMinuteBucket bucket) {
            buckets.add(bucket);
            return bucket;
        }

        @Override
        public List<TrafficMinuteBucket> findByProjectSince(String projectId, Instant since) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TrafficMinuteBucket> findSince(Instant since) {
            return buckets.stream()
                    .filter(bucket -> bucket.bucketStart() == null || !bucket.bucketStart().isBefore(since))
                    .toList();
        }

        @Override
        public TrafficSnapshot snapshot(String projectId, Instant from, Instant to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteOlderThan(Instant cutoff) {
            throw new UnsupportedOperationException();
        }
    }
}
