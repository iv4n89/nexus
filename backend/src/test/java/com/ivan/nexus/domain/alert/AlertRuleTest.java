package com.ivan.nexus.domain.alert;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertRuleTest {

    @Test
    void defensivelyCopiesThresholdsWhilePreservingOrderAndNullValues() {
        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("first", 70L);
        thresholds.put("unset", null);
        thresholds.put("last", 82.9);

        AlertRule rule = new AlertRule(
                UUID.randomUUID(), "lab", AlertType.HIGH_MEMORY, thresholds, true);
        thresholds.put("first", 99);

        assertThat(rule.thresholds().keySet()).containsExactly("first", "unset", "last");
        assertThat(rule.thresholds())
                .containsEntry("first", 70L)
                .containsEntry("unset", null)
                .containsEntry("last", 82.9);
        assertThatThrownBy(() -> rule.thresholds().put("new", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void thresholdUsesNumberIntValueSemantics() {
        AlertRule rule = new AlertRule(
                UUID.randomUUID(),
                null,
                AlertType.HIGH_MEMORY,
                Map.of("long", 91L, "decimal", 72.8, "text", "80"),
                true);

        assertThat(rule.threshold("long")).hasValue(91);
        assertThat(rule.threshold("decimal")).hasValue(72);
        assertThat(rule.threshold("text")).isEmpty();
        assertThat(rule.threshold("missing")).isEmpty();
    }

    @Test
    void nullThresholdMapBecomesEmpty() {
        AlertRule rule = new AlertRule(UUID.randomUUID(), null, AlertType.DISK, null, false);

        assertThat(rule.thresholds()).isEmpty();
    }
}
