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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersistAlertEvaluationTest {

    @Test
    void persistsFiringBeforeItsActivityThenResolveBeforeItsActivity() {
        AlertStore alerts = mock(AlertStore.class);
        RecordActivity activity = mock(RecordActivity.class);
        AlertKey firingKey = new AlertKey(AlertType.CONTAINER_STOPPED, "lab", "web");
        AlertKey resolveKey = new AlertKey(AlertType.DISK, null, null);
        Alert resolved = alert(resolveKey, AlertStatus.RESOLVED);
        when(alerts.findOpen(firingKey)).thenReturn(Optional.empty());
        when(alerts.resolve(resolveKey, NOW)).thenReturn(Optional.of(resolved));

        new PersistAlertEvaluation(alerts, activity).persist(
                new AlertEvaluation(
                        List.of(new AlertFiring(firingKey, "Container web is exited")),
                        List.of(resolveKey)),
                List.of(
                        rule(AlertType.CONTAINER_STOPPED),
                        rule(AlertType.DISK)),
                NOW);

        var order = inOrder(alerts, activity);
        order.verify(alerts).findOpen(firingKey);
        ArgumentCaptor<Alert> opened = ArgumentCaptor.forClass(Alert.class);
        order.verify(alerts).open(opened.capture());
        order.verify(activity).execute(
                ActivityType.ALERT_CREATED,
                "lab",
                "web",
                "alert created",
                Map.of("alertType", "CONTAINER_STOPPED", "detail", "Container web is exited"));
        order.verify(alerts).resolve(resolveKey, NOW);
        order.verify(activity).execute(
                ActivityType.ALERT_RESOLVED,
                null,
                null,
                "alert resolved",
                Map.of("alertType", "DISK"));

        assertThat(opened.getValue().id()).isNotNull();
        assertThat(opened.getValue().openedAt()).isEqualTo(NOW);
        assertThat(opened.getValue().status()).isEqualTo(AlertStatus.ACTIVE);
    }

    private static final Instant NOW = Instant.parse("2026-09-17T20:00:00Z");

    private static AlertRule rule(AlertType type) {
        return new AlertRule(UUID.randomUUID(), null, type, Map.of(), true);
    }

    private static Alert alert(AlertKey key, AlertStatus status) {
        return new Alert(
                UUID.randomUUID(),
                UUID.randomUUID(),
                key.projectId(),
                key.serviceId(),
                status,
                "resolved",
                NOW.minusSeconds(60),
                null,
                NOW,
                key.type());
    }
}
