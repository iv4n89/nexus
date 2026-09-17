package com.ivan.nexus.application.deployment;

import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetDeploymentHistory {
    private final DeploymentJpaRepository deployments;

    public GetDeploymentHistory(DeploymentJpaRepository deployments) {
        this.deployments = deployments;
    }

    @Transactional(readOnly = true)
    public List<DeploymentView> execute(String projectId) {
        return deployments.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(DeploymentView::from)
                .toList();
    }
}
