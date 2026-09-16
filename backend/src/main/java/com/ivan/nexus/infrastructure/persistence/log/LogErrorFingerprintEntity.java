package com.ivan.nexus.infrastructure.persistence.log;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "log_error_fingerprints")
public class LogErrorFingerprintEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "service_id", nullable = false, length = 64)
    private String serviceId;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Column(nullable = false)
    private long count;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "sample_message", nullable = false)
    private String sampleMessage;

    protected LogErrorFingerprintEntity() {
    }

    public LogErrorFingerprintEntity(
            UUID id,
            String projectId,
            String serviceId,
            String fingerprint,
            Instant firstSeen,
            Instant lastSeen,
            long count,
            String sampleMessage) {
        this.id = id;
        this.projectId = projectId;
        this.serviceId = serviceId;
        this.fingerprint = fingerprint;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.count = count;
        this.sampleMessage = sampleMessage;
    }

    public void recordHit(Instant at) {
        this.lastSeen = at;
        this.count++;
    }

    public UUID getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public long getCount() {
        return count;
    }

    public String getSampleMessage() {
        return sampleMessage;
    }
}
