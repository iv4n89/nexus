package com.ivan.nexus.application.log;

import com.ivan.nexus.application.alert.EvaluateAlerts;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeLogsConditionTest {

    @Test
    void analysisIsIndependentOfAlertsEnabled() {
        ConditionalOnProperty analysis = AnalyzeLogs.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(analysis).isNotNull();
        assertThat(analysis.name()).containsExactly("nexus.logs.analysis-enabled");
        assertThat(analysis.havingValue()).isEqualTo("true");
        assertThat(analysis.matchIfMissing()).isTrue();

        ConditionalOnProperty alerts = EvaluateAlerts.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(alerts.name()).containsExactly("nexus.alerts.enabled");
    }
}
