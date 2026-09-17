package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertEvaluation;
import com.ivan.nexus.domain.alert.AlertFiring;
import com.ivan.nexus.domain.alert.AlertKey;
import com.ivan.nexus.domain.alert.AlertRule;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PersistAlertEvaluation {
    private final AlertStore alerts;
    private final RecordActivity recordActivity;

    public PersistAlertEvaluation(AlertStore alerts, RecordActivity recordActivity) {
        this.alerts = alerts;
        this.recordActivity = recordActivity;
    }

    @Transactional
    public void persist(AlertEvaluation evaluation, List<AlertRule> enabledRules, Instant now) {
        for (AlertFiring firing : evaluation.firings()) {
            AlertKey key = firing.key();
            if (alerts.findOpen(key).isPresent()) {
                continue;
            }
            AlertRule rule = EvaluateAlerts.ruleFor(enabledRules, key.type(), key.projectId());
            if (rule == null) {
                continue;
            }
            alerts.open(new Alert(
                    UUID.randomUUID(),
                    rule.id(),
                    key.projectId(),
                    key.serviceId(),
                    AlertStatus.ACTIVE,
                    firing.message(),
                    now,
                    null,
                    null,
                    key.type()));
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
            alerts.resolve(key, now)
                    .ifPresent(resolved -> {
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
