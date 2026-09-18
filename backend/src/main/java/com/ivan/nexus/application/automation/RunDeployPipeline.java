package com.ivan.nexus.application.automation;

import com.ivan.nexus.application.backup.BackupPolicyStore;
import com.ivan.nexus.application.backup.RunBackup;
import com.ivan.nexus.application.deployment.DeployProject;
import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.application.security.RunSecurityScan;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupPolicy;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import com.ivan.nexus.domain.security.SecuritySeverity;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the deploy pipeline (H1): optional security gate → deploy → health wait →
 * traffic watch stub → optional post-deploy backup. All automation flags default OFF.
 */
@Service
public class RunDeployPipeline {
    private static final Logger log = LoggerFactory.getLogger(RunDeployPipeline.class);
    private static final Duration DEFAULT_HEALTH_WAIT = Duration.ofMinutes(5);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private final DeployProject deployProject;
    private final DeploymentStore deployments;
    private final Optional<RunSecurityScan> securityScan;
    private final Optional<RunBackup> runBackup;
    private final Optional<BackupPolicyStore> backupPolicies;
    private final PipelineFlags flags;
    private final Sleeper sleeper;
    private final Duration healthWait;

    @Autowired
    public RunDeployPipeline(
            DeployProject deployProject,
            DeploymentStore deployments,
            Optional<RunSecurityScan> securityScan,
            Optional<RunBackup> runBackup,
            Optional<BackupPolicyStore> backupPolicies,
            PipelineFlags flags) {
        this(deployProject, deployments, securityScan, runBackup, backupPolicies, flags,
                Thread::sleep, DEFAULT_HEALTH_WAIT);
    }

    RunDeployPipeline(
            DeployProject deployProject,
            DeploymentStore deployments,
            Optional<RunSecurityScan> securityScan,
            Optional<RunBackup> runBackup,
            Optional<BackupPolicyStore> backupPolicies,
            PipelineFlags flags,
            Sleeper sleeper,
            Duration healthWait) {
        this.deployProject = deployProject;
        this.deployments = deployments;
        this.securityScan = securityScan;
        this.runBackup = runBackup;
        this.backupPolicies = backupPolicies;
        this.flags = flags;
        this.sleeper = sleeper;
        this.healthWait = healthWait;
    }

    public PipelineResult execute(String projectId, String username) {
        List<SecurityFinding> findings = List.of();
        if (flags.securityGateEnabled()) {
            RunSecurityScan scan = securityScan.orElseThrow(() ->
                    new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "Security scanner not available"));
            findings = scan.execute(projectId);
            long critical = findings.stream()
                    .filter(f -> f.status() == SecurityFindingStatus.OPEN)
                    .filter(f -> f.severity() == SecuritySeverity.CRITICAL)
                    .count();
            if (critical > 0) {
                throw new DomainException(
                        NexusErrorCode.OPERATION_NOT_ALLOWED,
                        "Security gate blocked deploy: " + critical + " critical finding(s)");
            }
        }

        Deployment deployment = deployProject.execute(projectId, username);
        Deployment finished = waitForDeployment(deployment.id());

        if (flags.trafficWatchEnabled()) {
            log.info("Traffic watch stub for project {} after deploy {}", projectId, finished.id());
        }

        Backup backup = null;
        if (flags.postDeployBackupEnabled() && runBackup.isPresent()) {
            boolean policyEnabled = backupPolicies
                    .flatMap(store -> store.findByProjectId(projectId))
                    .map(BackupPolicy::enabled)
                    .orElse(false);
            if (policyEnabled) {
                backup = runBackup.get().execute(projectId, BackupKind.MANUAL);
            }
        }

        return new PipelineResult(finished, findings, backup, flags);
    }

    private Deployment waitForDeployment(UUID id) {
        long deadline = System.nanoTime() + healthWait.toNanos();
        while (System.nanoTime() < deadline) {
            Deployment current = deployments.findById(id)
                    .orElseThrow(() -> new DomainException(NexusErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment not found"));
            if (current.status() == DeploymentStatus.SUCCESS
                    || current.status() == DeploymentStatus.FAILED
                    || current.status() == DeploymentStatus.CANCELLED) {
                return current;
            }
            try {
                sleeper.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "Pipeline interrupted");
            }
        }
        throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "Timed out waiting for deploy health");
    }

    public record PipelineResult(
            Deployment deployment,
            List<SecurityFinding> securityFindings,
            Backup postDeployBackup,
            PipelineFlags flags) {
    }

    public record PipelineFlags(
            boolean securityGateEnabled,
            boolean trafficWatchEnabled,
            boolean postDeployBackupEnabled) {
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
