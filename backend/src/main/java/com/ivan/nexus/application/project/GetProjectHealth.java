package com.ivan.nexus.application.project;

import com.ivan.nexus.application.alert.AlertStore;
import com.ivan.nexus.application.backup.BackupStore;
import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.application.security.SecurityFindingStore;
import com.ivan.nexus.application.site.DomainStore;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupStatus;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class GetProjectHealth {
    private static final List<AlertStatus> OPEN_ALERTS =
            List.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED);

    private final GetProject getProject;
    private final DeploymentStore deployments;
    private final AlertStore alerts;
    private final Optional<SecurityFindingStore> findings;
    private final Optional<BackupStore> backups;
    private final Optional<DomainStore> domains;

    public GetProjectHealth(
            GetProject getProject,
            DeploymentStore deployments,
            AlertStore alerts,
            Optional<SecurityFindingStore> findings,
            Optional<BackupStore> backups,
            Optional<DomainStore> domains) {
        this.getProject = getProject;
        this.deployments = deployments;
        this.alerts = alerts;
        this.findings = findings;
        this.backups = backups;
        this.domains = domains;
    }

    @Transactional(readOnly = true)
    public Result execute(String projectId) {
        GetProject.Result project = getProject.execute(projectId);

        List<Deployment> history = deployments.findProjectHistoryNewestFirst(projectId);
        Deployment lastDeployment = history.isEmpty() ? null : history.getFirst();

        long openAlertsCount = alerts.latest(OPEN_ALERTS).stream()
                .filter(alert -> projectId.equals(alert.projectId()))
                .count();

        Integer openSecurityFindingsCount = findings
                .map(store -> (int) store.listByProject(projectId).stream()
                        .filter(finding -> finding.status() == SecurityFindingStatus.OPEN)
                        .count())
                .orElse(null);

        Instant lastBackupSuccessAt = null;
        boolean backupsAvailable = backups.isPresent();
        if (backupsAvailable) {
            lastBackupSuccessAt = backups.get().findByProjectIdNewestFirst(projectId).stream()
                    .filter(backup -> backup.status() == BackupStatus.SUCCESS)
                    .map(GetProjectHealth::backupTimestamp)
                    .findFirst()
                    .orElse(null);
        }

        Integer domainsCount = domains
                .map(store -> store.findByProjectId(projectId).size())
                .orElse(null);

        return new Result(
                project.project().id(),
                project.project().status(),
                project.project().runningCount(),
                project.project().totalCount(),
                lastDeployment == null ? null : lastDeployment.status(),
                lastDeployment == null ? null : deploymentTimestamp(lastDeployment),
                openAlertsCount,
                openSecurityFindingsCount,
                backupsAvailable,
                lastBackupSuccessAt,
                domainsCount);
    }

    private static Instant deploymentTimestamp(Deployment deployment) {
        if (deployment.finishedAt() != null) {
            return deployment.finishedAt();
        }
        return deployment.startedAt();
    }

    private static Instant backupTimestamp(Backup backup) {
        if (backup.finishedAt() != null) {
            return backup.finishedAt();
        }
        return backup.createdAt();
    }

    public record Result(
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
    }
}
