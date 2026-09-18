package com.ivan.nexus.application.backup;

import com.ivan.nexus.domain.backup.BackupPolicy;

import java.util.Optional;

public interface BackupPolicyStore {
    Optional<BackupPolicy> findByProjectId(String projectId);

    BackupPolicy upsert(BackupPolicy policy);
}
