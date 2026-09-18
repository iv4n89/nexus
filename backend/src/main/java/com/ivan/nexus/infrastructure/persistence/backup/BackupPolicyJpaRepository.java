package com.ivan.nexus.infrastructure.persistence.backup;

import org.springframework.data.jpa.repository.JpaRepository;

interface BackupPolicyJpaRepository extends JpaRepository<BackupPolicyEntity, String> {
}
