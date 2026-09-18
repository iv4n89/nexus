package com.ivan.nexus.infrastructure.persistence.backup;

import com.ivan.nexus.application.backup.BackupStore;
import com.ivan.nexus.domain.backup.Backup;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JpaBackupStore implements BackupStore {
    private final BackupJpaRepository repository;

    public JpaBackupStore(BackupJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Backup> findByProjectIdNewestFirst(String projectId) {
        return repository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(JpaBackupStore::toDomain)
                .toList();
    }

    @Override
    public Backup save(Backup backup) {
        return toDomain(repository.save(toEntity(backup)));
    }

    private static BackupEntity toEntity(Backup backup) {
        return new BackupEntity(
                backup.id(),
                backup.projectId(),
                backup.status(),
                backup.kind(),
                backup.createdAt(),
                backup.finishedAt(),
                backup.artifactUri(),
                backup.summary(),
                backup.includesDb(),
                backup.includesVolumes());
    }

    private static Backup toDomain(BackupEntity entity) {
        return new Backup(
                entity.getId(),
                entity.getProjectId(),
                entity.getStatus(),
                entity.getKind(),
                entity.getCreatedAt(),
                entity.getFinishedAt(),
                entity.getArtifactUri(),
                entity.getSummary(),
                entity.isIncludesDb(),
                entity.isIncludesVolumes());
    }
}
