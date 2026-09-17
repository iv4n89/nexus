package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetDeployment {
    private final DeploymentJpaRepository deployments;

    public GetDeployment(DeploymentJpaRepository deployments) {
        this.deployments = deployments;
    }

    @Transactional(readOnly = true)
    public DeploymentView execute(String projectId, UUID deploymentId) {
        return deployments.findByIdAndProjectId(deploymentId, projectId)
                .map(DeploymentView::from)
                .orElseThrow(() -> new DomainException(NexusErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment not found"));
    }
}
