package com.ivan.nexus.infrastructure.persistence.deployment;

import com.ivan.nexus.application.deployment.DeploymentEventStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JpaDeploymentEventStore implements DeploymentEventStore {
    private final DeploymentEventJpaRepository repository;

    JpaDeploymentEventStore(DeploymentEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(UUID deploymentId, String line) {
        repository.save(new DeploymentEventEntity(deploymentId, line));
    }

    @Override
    public List<String> findLinesOldestFirst(UUID deploymentId) {
        return repository.findByDeploymentIdOrderByIdAsc(deploymentId).stream()
                .map(DeploymentEventEntity::getLine)
                .toList();
    }

    @Override
    public long deleteCreatedBefore(Instant cutoff) {
        return repository.deleteByCreatedAtBefore(cutoff);
    }
}
