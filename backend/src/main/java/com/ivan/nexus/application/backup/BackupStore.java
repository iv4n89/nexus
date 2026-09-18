package com.ivan.nexus.application.backup;

import com.ivan.nexus.domain.backup.Backup;

import java.util.List;

public interface BackupStore {
    List<Backup> findByProjectIdNewestFirst(String projectId);

    Backup save(Backup backup);
}
