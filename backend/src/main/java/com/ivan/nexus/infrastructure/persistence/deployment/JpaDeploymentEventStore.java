package com.ivan.nexus.infrastructure.persistence.deployment;

import com.ivan.nexus.application.deployment.DeploymentEventStore;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JpaDeploymentEventStore implements DeploymentEventStore {
    private final DeploymentEventJpaRepository repository;

    public JpaDeploymentEventStore(DeploymentEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public long deleteCreatedBefore(Instant cutoff) {
        return repository.deleteByCreatedAtBefore(cutoff);
    }
}
