package com.ivan.nexus.infrastructure.persistence.env;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "project_env_vars",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_project_env_vars_project_name",
                columnNames = {"project_id", "name"}))
public class ProjectEnvVarEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(nullable = false, length = 255)
    private String name;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "encrypted_value", nullable = false)
    private String encryptedValue;

    @Column(nullable = false)
    private boolean secret;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectEnvVarEntity() {
    }

    public ProjectEnvVarEntity(
            UUID id,
            String projectId,
            String name,
            String encryptedValue,
            boolean secret,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.encryptedValue = encryptedValue;
        this.secret = secret;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void replace(String encryptedValue, boolean secret, Instant updatedAt) {
        this.encryptedValue = encryptedValue;
        this.secret = secret;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getEncryptedValue() {
        return encryptedValue;
    }

    public boolean isSecret() {
        return secret;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
