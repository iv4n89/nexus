package com.ivan.nexus.infrastructure.persistence.site;

import com.ivan.nexus.domain.site.CertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "site_domains")
public class SiteDomainEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(nullable = false, length = 253)
    private String hostname;

    @Column(name = "service_name", nullable = false, length = 128)
    private String serviceName;

    @Column(name = "target_port", nullable = false)
    private int targetPort;

    @Enumerated(EnumType.STRING)
    @Column(name = "cert_status", length = 16)
    private CertStatus certStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SiteDomainEntity() {
    }

    public SiteDomainEntity(
            UUID id,
            String projectId,
            String hostname,
            String serviceName,
            int targetPort,
            CertStatus certStatus,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.projectId = projectId;
        this.hostname = hostname;
        this.serviceName = serviceName;
        this.targetPort = targetPort;
        this.certStatus = certStatus;
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

    public UUID getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getHostname() {
        return hostname;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public CertStatus getCertStatus() {
        return certStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
