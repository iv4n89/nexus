package com.ivan.nexus.infrastructure.persistence.backup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface BackupJpaRepository extends JpaRepository<BackupEntity, UUID> {
    List<BackupEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
