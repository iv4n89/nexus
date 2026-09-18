package com.ivan.nexus.interfaces.project;

import com.ivan.nexus.application.project.GetProjectHealth;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/projects")
public class ProjectHealthController {
    private final GetProjectHealth getProjectHealth;

    public ProjectHealthController(GetProjectHealth getProjectHealth) {
        this.getProjectHealth = getProjectHealth;
    }

    @GetMapping("/{id}/health")
    public ProjectHealthResponse health(@PathVariable String id) {
        return ProjectHealthResponse.from(getProjectHealth.execute(id));
    }

    public record ProjectHealthResponse(
            String projectId,
            String projectStatus,
            int containersRunning,
            int containersTotal,
            DeploymentStatus lastDeploymentStatus,
            Instant lastDeploymentAt,
            long openAlertsCount,
            Integer openSecurityFindingsCount,
            boolean backupsAvailable,
            Instant lastBackupSuccessAt,
            Integer domainsCount) {

        static ProjectHealthResponse from(GetProjectHealth.Result result) {
            return new ProjectHealthResponse(
                    result.projectId(),
                    result.projectStatus(),
                    result.containersRunning(),
                    result.containersTotal(),
                    result.lastDeploymentStatus(),
                    result.lastDeploymentAt(),
                    result.openAlertsCount(),
                    result.openSecurityFindingsCount(),
                    result.backupsAvailable(),
                    result.lastBackupSuccessAt(),
                    result.domainsCount());
        }
    }
}
