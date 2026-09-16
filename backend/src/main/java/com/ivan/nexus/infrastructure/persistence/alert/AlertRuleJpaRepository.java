package com.ivan.nexus.infrastructure.persistence.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertRuleJpaRepository extends JpaRepository<AlertRuleEntity, UUID> {
    List<AlertRuleEntity> findByEnabledTrue();
}
