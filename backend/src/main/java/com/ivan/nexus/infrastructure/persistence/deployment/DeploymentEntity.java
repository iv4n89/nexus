package com.ivan.nexus.infrastructure.persistence.deployment;

import com.ivan.nexus.domain.deployment.DeploymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "deployments")
public class DeploymentEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeploymentStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "triggered_by", nullable = false, length = 64)
    private String triggeredBy;

    @Column(name = "commit_sha", length = 64)
    private String commitSha;

    @Column(name = "exit_code")
    private Integer exitCode;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "output_summary")
    private String outputSummary;

    @Column(name = "health_ok")
    private Boolean healthOk;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeploymentEntity() {
    }

    public DeploymentEntity(
            UUID id,
            String projectId,
            DeploymentStatus status,
            Instant startedAt,
            Instant finishedAt,
            String triggeredBy,
            String commitSha,
            Integer exitCode,
            String outputSummary,
            Boolean healthOk) {
        this(id, projectId, status, startedAt, finishedAt, triggeredBy, commitSha, exitCode, outputSummary, healthOk, Map.of());
    }

    public DeploymentEntity(
            UUID id,
            String projectId,
            DeploymentStatus status,
            Instant startedAt,
            Instant finishedAt,
            String triggeredBy,
            String commitSha,
            Integer exitCode,
            String outputSummary,
            Boolean healthOk,
            Map<String, Object> metadata) {
        this.id = id;
        this.projectId = projectId;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.triggeredBy = triggeredBy;
        this.commitSha = commitSha;
        this.exitCode = exitCode;
        this.outputSummary = outputSummary;
        this.healthOk = healthOk;
        this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void applyStatus(DeploymentStatus status) {
        this.status = status;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public void setOutputSummary(String outputSummary) {
        this.outputSummary = outputSummary;
    }

    public void setHealthOk(Boolean healthOk) {
        this.healthOk = healthOk;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }

    public UUID getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public DeploymentStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public Boolean getHealthOk() {
        return healthOk;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getKind() {
        if (metadata == null) {
            return null;
        }
        Object kind = metadata.get("kind");
        return kind == null ? null : kind.toString();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
