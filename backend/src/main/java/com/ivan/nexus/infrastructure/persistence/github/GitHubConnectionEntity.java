package com.ivan.nexus.infrastructure.persistence.github;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_connections")
public class GitHubConnectionEntity {

    @Id
    private UUID id;

    @Column(name = "github_login", nullable = false, length = 255)
    private String githubLogin;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "encrypted_token", nullable = false)
    private String encryptedToken;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "encrypted_refresh")
    private String encryptedRefresh;

    @Column(length = 512)
    private String scopes;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GitHubConnectionEntity() {
    }

    public GitHubConnectionEntity(
            UUID id,
            String githubLogin,
            String encryptedToken,
            String encryptedRefresh,
            String scopes,
            Instant connectedAt,
            Instant updatedAt) {
        this.id = id;
        this.githubLogin = githubLogin;
        this.encryptedToken = encryptedToken;
        this.encryptedRefresh = encryptedRefresh;
        this.scopes = scopes;
        this.connectedAt = connectedAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (connectedAt == null) {
            connectedAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void replace(
            String githubLogin,
            String encryptedToken,
            String encryptedRefresh,
            String scopes,
            Instant connectedAt,
            Instant updatedAt) {
        this.githubLogin = githubLogin;
        this.encryptedToken = encryptedToken;
        this.encryptedRefresh = encryptedRefresh;
        this.scopes = scopes;
        this.connectedAt = connectedAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getGithubLogin() {
        return githubLogin;
    }

    public String getEncryptedToken() {
        return encryptedToken;
    }

    public String getEncryptedRefresh() {
        return encryptedRefresh;
    }

    public String getScopes() {
        return scopes;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
