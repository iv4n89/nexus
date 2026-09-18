package com.ivan.nexus.application.backup;

import com.ivan.nexus.domain.backup.BackupPolicy;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpsertBackupPolicy {
    private final BackupPolicyStore store;

    public UpsertBackupPolicy(BackupPolicyStore store) {
        this.store = store;
    }

    @Transactional
    public BackupPolicy execute(BackupPolicy policy) {
        requireNonNegative(policy.dailyRetention(), "dailyRetention");
        requireNonNegative(policy.weeklyRetention(), "weeklyRetention");
        requireNonNegative(policy.monthlyRetention(), "monthlyRetention");
        String cron = blankToNull(policy.scheduleCron());
        return store.upsert(new BackupPolicy(
                policy.projectId(),
                policy.dailyRetention(),
                policy.weeklyRetention(),
                policy.monthlyRetention(),
                policy.enabled(),
                cron));
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, field + " must be >= 0");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
