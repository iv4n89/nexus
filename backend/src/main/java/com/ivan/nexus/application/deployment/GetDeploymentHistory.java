package com.ivan.nexus.application.deployment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetDeploymentHistory {
    private final DeploymentStore deployments;

    public GetDeploymentHistory(DeploymentStore deployments) {
        this.deployments = deployments;
    }

    @Transactional(readOnly = true)
    public List<DeploymentView> execute(String projectId) {
        return deployments.findProjectHistoryNewestFirst(projectId).stream()
                .map(DeploymentView::from)
                .toList();
    }
}
