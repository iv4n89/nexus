package com.ivan.nexus.infrastructure.persistence.alert;

import com.ivan.nexus.domain.alert.AlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_events")
public class AlertEventEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private AlertRuleEntity rule;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "service_id", length = 64)
    private String serviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertStatus status;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String message;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected AlertEventEntity() {
    }

    public AlertEventEntity(
            UUID id,
            AlertRuleEntity rule,
            String projectId,
            String serviceId,
            AlertStatus status,
            String message,
            Instant openedAt,
            Instant acknowledgedAt,
            Instant resolvedAt) {
        this.id = id;
        this.rule = rule;
        this.projectId = projectId;
        this.serviceId = serviceId;
        this.status = status;
        this.message = message;
        this.openedAt = openedAt;
        this.acknowledgedAt = acknowledgedAt;
        this.resolvedAt = resolvedAt;
    }

    public void acknowledge(Instant at) {
        this.status = AlertStatus.ACKNOWLEDGED;
        this.acknowledgedAt = at;
    }

    public void resolve(Instant at) {
        this.status = AlertStatus.RESOLVED;
        this.resolvedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public AlertRuleEntity getRule() {
        return rule;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

}
