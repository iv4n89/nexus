package com.ivan.nexus.infrastructure.persistence.env;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProjectEnvVarJpaRepository extends JpaRepository<ProjectEnvVarEntity, UUID> {
    List<ProjectEnvVarEntity> findByProjectIdOrderByNameAsc(String projectId);

    Optional<ProjectEnvVarEntity> findByProjectIdAndName(String projectId, String name);

    void deleteByProjectIdAndName(String projectId, String name);
}
