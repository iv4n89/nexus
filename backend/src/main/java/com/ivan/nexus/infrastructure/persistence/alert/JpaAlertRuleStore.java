package com.ivan.nexus.infrastructure.persistence.alert;

import com.ivan.nexus.application.alert.AlertRuleStore;
import com.ivan.nexus.domain.alert.AlertRule;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JpaAlertRuleStore implements AlertRuleStore {
    private final AlertRuleJpaRepository repository;

    public JpaAlertRuleStore(AlertRuleJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AlertRule> findEnabled() {
        return repository.findByEnabledTrue().stream()
                .map(JpaAlertRuleStore::toDomain)
                .toList();
    }

    private static AlertRule toDomain(AlertRuleEntity entity) {
        return new AlertRule(
                entity.getId(),
                entity.getProjectId(),
                entity.getType(),
                entity.getThresholdJson(),
                entity.isEnabled());
    }
}
