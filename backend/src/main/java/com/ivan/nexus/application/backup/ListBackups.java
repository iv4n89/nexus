package com.ivan.nexus.application.backup;

import com.ivan.nexus.domain.backup.Backup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListBackups {
    private final BackupStore store;

    public ListBackups(BackupStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public List<Backup> execute(String projectId) {
        return store.findByProjectIdNewestFirst(projectId);
    }
}
