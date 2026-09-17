package com.ivan.nexus.infrastructure.persistence.alert;

import com.ivan.nexus.application.alert.AlertStore;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertKey;
import com.ivan.nexus.domain.alert.AlertStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaAlertStore implements AlertStore {
    private final AlertEventJpaRepository events;
    private final AlertRuleJpaRepository rules;

    public JpaAlertStore(AlertEventJpaRepository events, AlertRuleJpaRepository rules) {
        this.events = events;
        this.rules = rules;
    }

    @Override
    public List<Alert> latest(Collection<AlertStatus> statuses) {
        return events.findByStatusInOrderByOpenedAtDesc(statuses).stream()
                .map(JpaAlertStore::toDomain)
                .toList();
    }

    @Override
    public Optional<Alert> findById(UUID id) {
        return events.findById(id).map(JpaAlertStore::toDomain);
    }

    @Override
    public Optional<Alert> findOpen(AlertKey key) {
        return findOpenEntity(key).map(JpaAlertStore::toDomain);
    }

    @Override
    public void open(Alert alert) {
        AlertRuleEntity rule = alert.ruleId() == null ? null : rules.getReferenceById(alert.ruleId());
        events.save(toEntity(alert, rule));
    }

    @Override
    public Optional<Alert> acknowledge(UUID id, Instant at) {
        return events.findById(id).map(entity -> {
            Alert acknowledged = toDomain(entity).acknowledge(at);
            entity.acknowledge(acknowledged.acknowledgedAt());
            events.save(entity);
            return toDomain(entity);
        });
    }

    @Override
    public Optional<Alert> resolve(AlertKey key, Instant at) {
        return findOpenEntity(key).map(entity -> {
            Alert resolved = toDomain(entity).resolve(at);
            entity.resolve(resolved.resolvedAt());
            events.save(entity);
            return toDomain(entity);
        });
    }

    private Optional<AlertEventEntity> findOpenEntity(AlertKey key) {
        return events.findOpenByTypeAndProjectAndService(
                key.type(), key.projectId(), key.serviceId());
    }

    private static AlertEventEntity toEntity(Alert alert, AlertRuleEntity rule) {
        return new AlertEventEntity(
                alert.id(),
                rule,
                alert.projectId(),
                alert.serviceId(),
                alert.status(),
                alert.message(),
                alert.openedAt(),
                alert.acknowledgedAt(),
                alert.resolvedAt());
    }

    private static Alert toDomain(AlertEventEntity entity) {
        AlertRuleEntity rule = entity.getRule();
        return new Alert(
                entity.getId(),
                rule == null ? null : rule.getId(),
                entity.getProjectId(),
                entity.getServiceId(),
                entity.getStatus(),
                entity.getMessage(),
                entity.getOpenedAt(),
                entity.getAcknowledgedAt(),
                entity.getResolvedAt(),
                rule == null ? null : rule.getType());
    }
}
