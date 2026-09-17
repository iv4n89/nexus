package com.ivan.nexus.infrastructure.persistence.alert;

import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertEventJpaRepository extends JpaRepository<AlertEventEntity, UUID> {

    @EntityGraph(attributePaths = "rule")
    List<AlertEventEntity> findByStatusInOrderByOpenedAtDesc(
            Collection<AlertStatus> statuses,
            Pageable pageable);

    @EntityGraph(attributePaths = "rule")
    @Query("""
            SELECT e FROM AlertEventEntity e
            JOIN e.rule r
            WHERE r.type = :type
              AND e.status IN (com.ivan.nexus.domain.alert.AlertStatus.ACTIVE,
                               com.ivan.nexus.domain.alert.AlertStatus.ACKNOWLEDGED)
              AND ((:projectId IS NULL AND e.projectId IS NULL) OR e.projectId = :projectId)
              AND ((:serviceId IS NULL AND e.serviceId IS NULL) OR e.serviceId = :serviceId)
            """)
    Optional<AlertEventEntity> findOpenByTypeAndProjectAndService(
            @Param("type") AlertType type,
            @Param("projectId") String projectId,
            @Param("serviceId") String serviceId);

    @Override
    @EntityGraph(attributePaths = "rule")
    Optional<AlertEventEntity> findById(UUID id);
}
