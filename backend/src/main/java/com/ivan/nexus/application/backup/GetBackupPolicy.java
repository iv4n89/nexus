package com.ivan.nexus.application.backup;

import com.ivan.nexus.domain.backup.BackupPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetBackupPolicy {
    private final BackupPolicyStore store;

    public GetBackupPolicy(BackupPolicyStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public BackupPolicy execute(String projectId) {
        return store.findByProjectId(projectId).orElseGet(() -> BackupPolicy.defaults(projectId));
    }
}
