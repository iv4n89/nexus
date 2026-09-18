package com.ivan.nexus.infrastructure.persistence.backup;

import com.ivan.nexus.application.backup.BackupPolicyStore;
import com.ivan.nexus.domain.backup.BackupPolicy;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JpaBackupPolicyStore implements BackupPolicyStore {
    private final BackupPolicyJpaRepository repository;

    public JpaBackupPolicyStore(BackupPolicyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BackupPolicy> findByProjectId(String projectId) {
        return repository.findById(projectId).map(JpaBackupPolicyStore::toDomain);
    }

    @Override
    public BackupPolicy upsert(BackupPolicy policy) {
        BackupPolicyEntity entity = repository.findById(policy.projectId())
                .orElseGet(() -> new BackupPolicyEntity(
                        policy.projectId(),
                        policy.dailyRetention(),
                        policy.weeklyRetention(),
                        policy.monthlyRetention(),
                        policy.enabled(),
                        policy.scheduleCron()));
        entity.apply(
                policy.dailyRetention(),
                policy.weeklyRetention(),
                policy.monthlyRetention(),
                policy.enabled(),
                policy.scheduleCron());
        return toDomain(repository.save(entity));
    }

    private static BackupPolicy toDomain(BackupPolicyEntity entity) {
        return new BackupPolicy(
                entity.getProjectId(),
                entity.getDailyRetention(),
                entity.getWeeklyRetention(),
                entity.getMonthlyRetention(),
                entity.isEnabled(),
                entity.getScheduleCron());
    }
}
