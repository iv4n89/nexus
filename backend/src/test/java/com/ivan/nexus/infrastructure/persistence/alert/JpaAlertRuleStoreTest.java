package com.ivan.nexus.infrastructure.persistence.alert;

import com.ivan.nexus.application.alert.AlertRuleStore;
import com.ivan.nexus.domain.alert.AlertRule;
import com.ivan.nexus.domain.alert.AlertType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaAlertRuleStoreTest {

    @Test
    void findEnabledMapsEntitiesToImmutableDomainRules() {
        AlertRuleJpaRepository repository = mock(AlertRuleJpaRepository.class);
        UUID id = UUID.randomUUID();
        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("memoryPercent", 75L);
        thresholds.put("optional", null);
        AlertRuleEntity entity = new AlertRuleEntity(
                id, "lab", AlertType.HIGH_MEMORY, thresholds, true);
        when(repository.findByEnabledTrue()).thenReturn(List.of(entity));

        AlertRuleStore store = new JpaAlertRuleStore(repository);
        List<AlertRule> result = store.findEnabled();
        entity.getThresholdJson().put("memoryPercent", 99);

        Map<String, Object> expectedThresholds = new LinkedHashMap<>();
        expectedThresholds.put("memoryPercent", 75L);
        expectedThresholds.put("optional", null);
        assertThat(result).containsExactly(
                new AlertRule(id, "lab", AlertType.HIGH_MEMORY, expectedThresholds, true));
    }
}
