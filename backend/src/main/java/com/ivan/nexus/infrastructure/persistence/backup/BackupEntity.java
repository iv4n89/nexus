package com.ivan.nexus.infrastructure.persistence.backup;

import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backups")
public class BackupEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BackupStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BackupKind kind;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "artifact_uri")
    private String artifactUri;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String summary;

    @Column(name = "includes_db", nullable = false)
    private boolean includesDb;

    @Column(name = "includes_volumes", nullable = false)
    private boolean includesVolumes;

    protected BackupEntity() {
    }

    public BackupEntity(
            UUID id,
            String projectId,
            BackupStatus status,
            BackupKind kind,
            Instant createdAt,
            Instant finishedAt,
            String artifactUri,
            String summary,
            boolean includesDb,
            boolean includesVolumes) {
        this.id = id;
        this.projectId = projectId;
        this.status = status;
        this.kind = kind;
        this.createdAt = createdAt;
        this.finishedAt = finishedAt;
        this.artifactUri = artifactUri;
        this.summary = summary;
        this.includesDb = includesDb;
        this.includesVolumes = includesVolumes;
    }

    public UUID getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public BackupStatus getStatus() {
        return status;
    }

    public BackupKind getKind() {
        return kind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getArtifactUri() {
        return artifactUri;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isIncludesDb() {
        return includesDb;
    }

    public boolean isIncludesVolumes() {
        return includesVolumes;
    }
}
