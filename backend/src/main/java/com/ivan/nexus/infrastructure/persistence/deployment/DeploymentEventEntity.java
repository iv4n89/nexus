package com.ivan.nexus.infrastructure.persistence.deployment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deployment_events")
public class DeploymentEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", nullable = false)
    private UUID deploymentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String line;

    protected DeploymentEventEntity() {
    }

    public DeploymentEventEntity(UUID deploymentId, String line) {
        this.deploymentId = deploymentId;
        this.line = line;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getDeploymentId() {
        return deploymentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getLine() {
        return line;
    }
}
