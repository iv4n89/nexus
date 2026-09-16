package com.ivan.nexus.infrastructure.persistence.deployment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DeploymentEventJpaRepository extends JpaRepository<DeploymentEventEntity, Long> {
    List<DeploymentEventEntity> findByDeploymentIdOrderByIdAsc(UUID deploymentId);

    long deleteByCreatedAtBefore(Instant cutoff);
}
