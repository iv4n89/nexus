package com.ivan.nexus.infrastructure.persistence.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "projects")
public class ManagedProjectEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String description;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "working_directory", nullable = false)
    private String workingDirectory;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "manifest_path", nullable = false)
    private String manifestPath;

    @Column(name = "github_owner", length = 255)
    private String githubOwner;

    @Column(name = "github_repo", length = 255)
    private String githubRepo;

    @Column(name = "github_branch", length = 255)
    private String githubBranch;

    @Column(name = "autodeploy_enabled", nullable = false)
    private boolean autodeployEnabled;

    @Column(name = "github_last_remote_sha", length = 64)
    private String githubLastRemoteSha;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ManagedProjectEntity() {
    }

    public ManagedProjectEntity(
            String id,
            String name,
            String description,
            String workingDirectory,
            String manifestPath) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.workingDirectory = workingDirectory;
        this.manifestPath = manifestPath;
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

    public void applyManifest(
            String name,
            String description,
            String workingDirectory,
            String manifestPath) {
        this.name = name;
        this.description = description;
        this.workingDirectory = workingDirectory;
        this.manifestPath = manifestPath;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public String getManifestPath() {
        return manifestPath;
    }

    public String getGithubOwner() {
        return githubOwner;
    }

    public String getGithubRepo() {
        return githubRepo;
    }

    public String getGithubBranch() {
        return githubBranch;
    }

    public boolean isAutodeployEnabled() {
        return autodeployEnabled;
    }

    public String getGithubLastRemoteSha() {
        return githubLastRemoteSha;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
