package com.ivan.nexus.application.automation;

import com.ivan.nexus.application.backup.BackupPolicyStore;
import com.ivan.nexus.application.backup.RunBackup;
import com.ivan.nexus.application.deployment.DeployProject;
import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.application.security.RunSecurityScan;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupPolicy;
import com.ivan.nexus.domain.backup.BackupStatus;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import com.ivan.nexus.domain.security.SecuritySeverity;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RunDeployPipelineTest {

    @Mock
    DeployProject deployProject;
    @Mock
    DeploymentStore deployments;
    @Mock
    RunSecurityScan securityScan;
    @Mock
    RunBackup runBackup;
    @Mock
    BackupPolicyStore backupPolicies;

    @Test
    void deploysWithoutOptionalStepsWhenFlagsOff() {
        UUID id = UUID.randomUUID();
        Deployment running = deployment(id, DeploymentStatus.RUNNING);
        Deployment success = deployment(id, DeploymentStatus.SUCCESS);
        given(deployProject.execute("lab", "admin")).willReturn(running);
        AtomicInteger polls = new AtomicInteger();
        given(deployments.findById(id)).willAnswer(inv -> {
            if (polls.incrementAndGet() == 1) {
                return Optional.of(running);
            }
            return Optional.of(success);
        });

        RunDeployPipeline.PipelineResult result = new RunDeployPipeline(
                deployProject,
                deployments,
                Optional.of(securityScan),
                Optional.of(runBackup),
                Optional.of(backupPolicies),
                new RunDeployPipeline.PipelineFlags(false, false, false),
                millis -> {},
                Duration.ofSeconds(5))
                .execute("lab", "admin");

        assertThat(result.deployment().status()).isEqualTo(DeploymentStatus.SUCCESS);
        verify(securityScan, never()).execute(any());
        verify(runBackup, never()).execute(any(), any());
    }

    @Test
    void securityGateBlocksOnCriticalFindings() {
        given(securityScan.execute("lab")).willReturn(List.of(finding(SecuritySeverity.CRITICAL)));

        assertThatThrownBy(() -> new RunDeployPipeline(
                deployProject,
                deployments,
                Optional.of(securityScan),
                Optional.of(runBackup),
                Optional.of(backupPolicies),
                new RunDeployPipeline.PipelineFlags(true, false, false),
                millis -> {},
                Duration.ofSeconds(5))
                .execute("lab", "admin"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.OPERATION_NOT_ALLOWED));

        verify(deployProject, never()).execute(any(), any());
    }

    @Test
    void postDeployBackupRunsWhenPolicyEnabled() {
        UUID id = UUID.randomUUID();
        Deployment success = deployment(id, DeploymentStatus.SUCCESS);
        given(deployProject.execute("lab", "admin")).willReturn(success);
        given(deployments.findById(id)).willReturn(Optional.of(success));
        given(backupPolicies.findByProjectId("lab")).willReturn(Optional.of(
                new BackupPolicy("lab", 7, 4, 3, true, null)));
        Backup backup = new Backup(
                UUID.randomUUID(), "lab", BackupStatus.SUCCESS, BackupKind.MANUAL,
                Instant.parse("2026-09-18T12:00:00Z"), Instant.parse("2026-09-18T12:01:00Z"),
                "file:///tmp/a", "ok", true, false);
        given(runBackup.execute("lab", BackupKind.MANUAL)).willReturn(backup);

        RunDeployPipeline.PipelineResult result = new RunDeployPipeline(
                deployProject,
                deployments,
                Optional.of(securityScan),
                Optional.of(runBackup),
                Optional.of(backupPolicies),
                new RunDeployPipeline.PipelineFlags(false, true, true),
                millis -> {},
                Duration.ofSeconds(5))
                .execute("lab", "admin");

        assertThat(result.postDeployBackup()).isEqualTo(backup);
        verify(runBackup).execute("lab", BackupKind.MANUAL);
    }

    private static Deployment deployment(UUID id, DeploymentStatus status) {
        return new Deployment(
                id,
                "lab",
                status,
                Instant.parse("2026-09-18T12:00:00Z"),
                status == DeploymentStatus.RUNNING ? null : Instant.parse("2026-09-18T12:01:00Z"),
                "admin",
                "abc",
                status == DeploymentStatus.SUCCESS ? 0 : null,
                "ok",
                status == DeploymentStatus.SUCCESS ? true : null,
                "deploy");
    }

    private static SecurityFinding finding(SecuritySeverity severity) {
        return new SecurityFinding(
                UUID.randomUUID(),
                "lab",
                severity,
                "trivy",
                "pkg",
                "1.0",
                "1.1",
                "crit",
                "fp",
                SecurityFindingStatus.OPEN,
                Instant.parse("2026-09-18T12:00:00Z"),
                Instant.parse("2026-09-18T12:00:00Z"));
    }
}
