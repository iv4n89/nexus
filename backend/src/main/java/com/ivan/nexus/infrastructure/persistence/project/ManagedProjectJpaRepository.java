package com.ivan.nexus.infrastructure.persistence.project;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedProjectJpaRepository extends JpaRepository<ManagedProjectEntity, String> {
}
