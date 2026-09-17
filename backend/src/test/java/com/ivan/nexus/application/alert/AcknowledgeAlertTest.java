package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class AcknowledgeAlertTest {

    @Mock
    AlertStore alerts;
    @Mock
    UserDirectory users;
    @Mock
    RecordAudit recordAudit;

    @Test
    void missingAlertThrowsNotFound() {
        UUID id = UUID.randomUUID();
        given(alerts.acknowledge(eq(id), any())).willReturn(Optional.empty());

        AcknowledgeAlert acknowledgeAlert = new AcknowledgeAlert(alerts, users, recordAudit);

        assertThatThrownBy(() -> acknowledgeAlert.execute(id, "admin", "127.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode()).isEqualTo(NexusErrorCode.ALERT_NOT_FOUND));
    }

    @Test
    void acknowledgesAndWritesAudit() {
        UUID id = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        Instant acknowledgedAt = Instant.parse("2026-01-01T00:01:00Z");
        Alert acknowledged = new Alert(
                id,
                ruleId,
                null,
                null,
                AlertStatus.ACKNOWLEDGED,
                "Host disk is 90%",
                Instant.parse("2026-01-01T00:00:00Z"),
                acknowledgedAt,
                null,
                AlertType.DISK);
        given(alerts.acknowledge(eq(id), any())).willReturn(Optional.of(acknowledged));
        given(users.findIdByUsername("admin")).willReturn(Optional.empty());

        Alert result = new AcknowledgeAlert(alerts, users, recordAudit).execute(id, "admin", "10.0.0.1");

        assertThat(result).isEqualTo(new Alert(
                id,
                ruleId,
                null,
                null,
                AlertStatus.ACKNOWLEDGED,
                "Host disk is 90%",
                Instant.parse("2026-01-01T00:00:00Z"),
                acknowledgedAt,
                null,
                AlertType.DISK));
        var order = inOrder(alerts, users, recordAudit);
        order.verify(alerts).acknowledge(eq(id), any());
        order.verify(users).findIdByUsername("admin");
        order.verify(recordAudit).execute(
                null,
                AuditAction.ALERT_ACKNOWLEDGE,
                null,
                null,
                "10.0.0.1",
                Map.of("alertId", id.toString()));
    }
}
