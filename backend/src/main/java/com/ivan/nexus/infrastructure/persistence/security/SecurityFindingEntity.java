package com.ivan.nexus.infrastructure.persistence.security;

import com.ivan.nexus.domain.security.SecurityFindingStatus;
import com.ivan.nexus.domain.security.SecuritySeverity;
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
@Table(name = "security_findings")
public class SecurityFindingEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SecuritySeverity severity;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "package_name", length = 256)
    private String packageName;

    @Column(name = "installed_version", length = 128)
    private String installedVersion;

    @Column(name = "fixed_version", length = 128)
    private String fixedVersion;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SecurityFindingStatus status;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    protected SecurityFindingEntity() {
    }

    public SecurityFindingEntity(
            UUID id,
            String projectId,
            SecuritySeverity severity,
            String source,
            String packageName,
            String installedVersion,
            String fixedVersion,
            String title,
            String fingerprint,
            SecurityFindingStatus status,
            Instant firstSeen,
            Instant lastSeen) {
        this.id = id;
        this.projectId = projectId;
        this.severity = severity;
        this.source = source;
        this.packageName = packageName;
        this.installedVersion = installedVersion;
        this.fixedVersion = fixedVersion;
        this.title = title;
        this.fingerprint = fingerprint;
        this.status = status;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
    }

    public void applyUpsert(
            SecuritySeverity severity,
            String source,
            String packageName,
            String installedVersion,
            String fixedVersion,
            String title,
            SecurityFindingStatus status,
            Instant lastSeen) {
        this.severity = severity;
        this.source = source;
        this.packageName = packageName;
        this.installedVersion = installedVersion;
        this.fixedVersion = fixedVersion;
        this.title = title;
        this.status = status;
        this.lastSeen = lastSeen;
    }

    public void acknowledge() {
        this.status = SecurityFindingStatus.ACKNOWLEDGED;
    }

    public UUID getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public SecuritySeverity getSeverity() {
        return severity;
    }

    public String getSource() {
        return source;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getInstalledVersion() {
        return installedVersion;
    }

    public String getFixedVersion() {
        return fixedVersion;
    }

    public String getTitle() {
        return title;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public SecurityFindingStatus getStatus() {
        return status;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }
}
