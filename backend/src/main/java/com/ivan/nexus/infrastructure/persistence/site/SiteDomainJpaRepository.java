package com.ivan.nexus.infrastructure.persistence.site;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SiteDomainJpaRepository extends JpaRepository<SiteDomainEntity, UUID> {
    List<SiteDomainEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);

    Optional<SiteDomainEntity> findByHostname(String hostname);
}
