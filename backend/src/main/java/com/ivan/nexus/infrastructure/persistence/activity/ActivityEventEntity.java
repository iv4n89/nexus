package com.ivan.nexus.infrastructure.persistence.activity;

import com.ivan.nexus.domain.activity.Activity;
import com.ivan.nexus.domain.activity.ActivityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "activity_events")
public class ActivityEventEntity {

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ActivityType type;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "service_id", length = 64)
    private String serviceId;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> metadata;

    protected ActivityEventEntity() {
    }

    public ActivityEventEntity(
            UUID id,
            Instant createdAt,
            ActivityType type,
            String projectId,
            String serviceId,
            String message,
            Map<String, Object> metadata) {
        this.id = id;
        this.createdAt = createdAt;
        this.type = type;
        this.projectId = projectId;
        this.serviceId = serviceId;
        this.message = message;
        this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ActivityType getType() {
        return type;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Activity toDomain() {
        return new Activity(id, createdAt, type, projectId, serviceId, message, metadata);
    }
}
