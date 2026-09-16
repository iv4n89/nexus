package com.ivan.nexus.infrastructure.persistence.audit;

import com.ivan.nexus.domain.audit.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "service_id", length = 64)
    private String serviceId;

    @Column(length = 64)
    private String ip;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(
            UUID id,
            UUID userId,
            AuditAction action,
            String projectId,
            String serviceId,
            String ip,
            Map<String, Object> metadata,
            Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.action = action;
        this.projectId = projectId;
        this.serviceId = serviceId;
        this.ip = ip;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getIp() {
        return ip;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
