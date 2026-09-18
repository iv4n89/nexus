package com.ivan.nexus.application.backup;

import com.ivan.nexus.domain.backup.Backup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BackupStore {
    List<Backup> findByProjectIdNewestFirst(String projectId);

    Optional<Backup> findById(UUID id);

    Backup save(Backup backup);

    void delete(UUID id);
}
