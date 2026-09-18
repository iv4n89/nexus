package com.ivan.nexus.infrastructure.persistence.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SecurityFindingJpaRepository extends JpaRepository<SecurityFindingEntity, UUID> {

    Optional<SecurityFindingEntity> findByProjectIdAndFingerprint(String projectId, String fingerprint);

    List<SecurityFindingEntity> findByProjectIdOrderByLastSeenDesc(String projectId);
}
