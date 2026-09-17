package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventEntity;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventJpaRepository;
import com.ivan.nexus.infrastructure.persistence.alert.AlertRuleEntity;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AcknowledgeAlertTest {

    @Mock
    AlertEventJpaRepository events;
    @Mock
    UserDirectory users;
    @Mock
    RecordAudit recordAudit;

    @Test
    void missingAlertThrowsNotFound() {
        UUID id = UUID.randomUUID();
        given(events.findById(id)).willReturn(Optional.empty());

        AcknowledgeAlert acknowledgeAlert = new AcknowledgeAlert(events, users, recordAudit);

        assertThatThrownBy(() -> acknowledgeAlert.execute(id, "admin", "127.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode()).isEqualTo(NexusErrorCode.ALERT_NOT_FOUND));
    }

    @Test
    void acknowledgesAndWritesAudit() {
        UUID id = UUID.randomUUID();
        AlertEventEntity event = new AlertEventEntity(
                id,
                new AlertRuleEntity(UUID.randomUUID(), null, AlertType.DISK, Map.of(), true),
                null,
                null,
                AlertStatus.ACTIVE,
                "Host disk is 90%",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null);
        given(events.findById(id)).willReturn(Optional.of(event));
        given(users.findIdByUsername("admin")).willReturn(Optional.empty());
        given(events.save(event)).willReturn(event);

        Alert result = new AcknowledgeAlert(events, users, recordAudit).execute(id, "admin", "10.0.0.1");

        assertThat(event.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(event.getAcknowledgedAt()).isNotNull();
        assertThat(result).isEqualTo(new Alert(
                id,
                event.getRule().getId(),
                null,
                null,
                AlertStatus.ACKNOWLEDGED,
                "Host disk is 90%",
                Instant.parse("2026-01-01T00:00:00Z"),
                event.getAcknowledgedAt(),
                null,
                AlertType.DISK));
        verify(recordAudit).execute(
                eq(null),
                eq(AuditAction.ALERT_ACKNOWLEDGE),
                eq(null),
                eq(null),
                eq("10.0.0.1"),
                eq(Map.of("alertId", id.toString())));
    }
}
