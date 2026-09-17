package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.alert.AlertEvaluation;
import com.ivan.nexus.domain.alert.AlertFiring;
import com.ivan.nexus.domain.alert.AlertKey;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventEntity;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventJpaRepository;
import com.ivan.nexus.infrastructure.persistence.alert.AlertRuleEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PersistAlertEvaluation {
    private final AlertEventJpaRepository events;
    private final RecordActivity recordActivity;

    public PersistAlertEvaluation(AlertEventJpaRepository events, RecordActivity recordActivity) {
        this.events = events;
        this.recordActivity = recordActivity;
    }

    @Transactional
    public void persist(AlertEvaluation evaluation, List<AlertRuleEntity> enabledRules, Instant now) {
        for (AlertFiring firing : evaluation.firings()) {
            AlertKey key = firing.key();
            if (events.findOpenByTypeAndProjectAndService(key.type(), key.projectId(), key.serviceId()).isPresent()) {
                continue;
            }
            AlertRuleEntity rule = EvaluateAlerts.ruleFor(enabledRules, key.type(), key.projectId());
            if (rule == null) {
                continue;
            }
            events.save(new AlertEventEntity(
                    UUID.randomUUID(),
                    rule,
                    key.projectId(),
                    key.serviceId(),
                    AlertStatus.ACTIVE,
                    firing.message(),
                    now,
                    null,
                    null));
            recordActivity.execute(
                    ActivityType.ALERT_CREATED,
                    key.projectId(),
                    key.serviceId(),
                    "alert created",
                    Map.of("alertType", key.type().name(), "detail", firing.message()));
            if (key.type() == AlertType.HTTP_HEALTH || key.type() == AlertType.DOCKER_HEALTH) {
                recordActivity.execute(
                        ActivityType.HEALTH_CHECK_FAILED,
                        key.projectId(),
                        key.serviceId(),
                        "health check failed",
                        Map.of("alertType", key.type().name()));
            }
        }
        for (AlertKey key : evaluation.resolveKeys()) {
            events.findOpenByTypeAndProjectAndService(key.type(), key.projectId(), key.serviceId())
                    .ifPresent(open -> {
                        open.resolve(now);
                        events.save(open);
                        recordActivity.execute(
                                ActivityType.ALERT_RESOLVED,
                                key.projectId(),
                                key.serviceId(),
                                "alert resolved",
                                Map.of("alertType", key.type().name()));
                    });
        }
    }
}
