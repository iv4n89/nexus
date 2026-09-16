package com.ivan.nexus.infrastructure.persistence.deployment;

import com.ivan.nexus.domain.deployment.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DeploymentJpaRepository extends JpaRepository<DeploymentEntity, UUID> {
    boolean existsByProjectIdAndStatusIn(String projectId, Collection<DeploymentStatus> statuses);

    List<DeploymentEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
