package com.ivan.nexus.infrastructure.persistence.security;

import com.ivan.nexus.application.security.SecurityFindingStore;
import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaSecurityFindingStore implements SecurityFindingStore {
    private final SecurityFindingJpaRepository findings;

    public JpaSecurityFindingStore(SecurityFindingJpaRepository findings) {
        this.findings = findings;
    }

    @Override
    public SecurityFinding upsertByFingerprint(SecurityFinding finding) {
        Optional<SecurityFindingEntity> existing =
                findings.findByProjectIdAndFingerprint(finding.projectId(), finding.fingerprint());
        if (existing.isPresent()) {
            SecurityFindingEntity entity = existing.get();
            SecurityFinding updated = toDomain(entity).seenAgain(
                    finding.severity(),
                    finding.source(),
                    finding.packageName(),
                    finding.installedVersion(),
                    finding.fixedVersion(),
                    finding.title(),
                    finding.lastSeen());
            entity.applyUpsert(
                    updated.severity(),
                    updated.source(),
                    updated.packageName(),
                    updated.installedVersion(),
                    updated.fixedVersion(),
                    updated.title(),
                    updated.status(),
                    updated.lastSeen());
            return toDomain(findings.save(entity));
        }

        SecurityFindingEntity created = toEntity(finding);
        return toDomain(findings.save(created));
    }

    @Override
    public List<SecurityFinding> listByProject(String projectId) {
        return findings.findByProjectIdOrderByLastSeenDesc(projectId).stream()
                .map(JpaSecurityFindingStore::toDomain)
                .toList();
    }

    @Override
    public Optional<SecurityFinding> findById(UUID id) {
        return findings.findById(id).map(JpaSecurityFindingStore::toDomain);
    }

    @Override
    public Optional<SecurityFinding> acknowledge(UUID id, Instant at) {
        return findings.findById(id).map(entity -> {
            SecurityFinding acknowledged = toDomain(entity).acknowledge(at);
            entity.acknowledge();
            findings.save(entity);
            return toDomain(entity);
        });
    }

    private static SecurityFindingEntity toEntity(SecurityFinding finding) {
        return new SecurityFindingEntity(
                finding.id(),
                finding.projectId(),
                finding.severity(),
                finding.source(),
                finding.packageName(),
                finding.installedVersion(),
                finding.fixedVersion(),
                finding.title(),
                finding.fingerprint(),
                finding.status() == null ? SecurityFindingStatus.OPEN : finding.status(),
                finding.firstSeen(),
                finding.lastSeen());
    }

    private static SecurityFinding toDomain(SecurityFindingEntity entity) {
        return new SecurityFinding(
                entity.getId(),
                entity.getProjectId(),
                entity.getSeverity(),
                entity.getSource(),
                entity.getPackageName(),
                entity.getInstalledVersion(),
                entity.getFixedVersion(),
                entity.getTitle(),
                entity.getFingerprint(),
                entity.getStatus(),
                entity.getFirstSeen(),
                entity.getLastSeen());
    }
}
