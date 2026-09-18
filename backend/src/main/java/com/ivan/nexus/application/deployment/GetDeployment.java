package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetDeployment {
    private final DeploymentStore deployments;

    public GetDeployment(DeploymentStore deployments) {
        this.deployments = deployments;
    }

    @Transactional(readOnly = true)
    public DeploymentView execute(String projectId, UUID deploymentId) {
        return deployments.findById(deploymentId)
                .filter(deployment -> deployment.projectId().equals(projectId))
                .map(DeploymentView::from)
                .orElseThrow(() -> new DomainException(NexusErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment not found"));
    }
}
