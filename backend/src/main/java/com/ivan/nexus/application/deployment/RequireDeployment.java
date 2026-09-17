package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RequireDeployment {
    private final DeploymentStore deployments;

    public RequireDeployment(DeploymentStore deployments) {
        this.deployments = deployments;
    }

    @Transactional(readOnly = true)
    public void execute(UUID id) {
        if (deployments.findById(id).isEmpty()) {
            throw new DomainException(NexusErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment not found");
        }
    }
}
