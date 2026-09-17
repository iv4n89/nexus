package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RequireDeployment {
    private final DeploymentJpaRepository deployments;

    public RequireDeployment(DeploymentJpaRepository deployments) {
        this.deployments = deployments;
    }

    @Transactional(readOnly = true)
    public void execute(UUID id) {
        if (!deployments.existsById(id)) {
            throw new DomainException(NexusErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment not found");
        }
    }
}
