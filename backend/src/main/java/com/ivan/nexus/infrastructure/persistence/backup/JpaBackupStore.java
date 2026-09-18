package com.ivan.nexus.infrastructure.persistence.backup;

import com.ivan.nexus.application.backup.BackupStore;
import com.ivan.nexus.domain.backup.Backup;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public Optional<Backup> findById(UUID id) {
        return repository.findById(id).map(JpaBackupStore::toDomain);
    }

    @Override
    public Backup save(Backup backup) {
        return toDomain(repository.save(toEntity(backup)));
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
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
