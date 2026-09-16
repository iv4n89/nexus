package com.ivan.nexus.infrastructure.persistence.activity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ActivityEventJpaRepository extends JpaRepository<ActivityEventEntity, UUID> {
    List<ActivityEventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long deleteByCreatedAtBefore(Instant cutoff);
}
