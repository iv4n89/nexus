package com.ivan.nexus.application.project;

import com.ivan.nexus.application.alert.AlertStore;
import com.ivan.nexus.application.backup.BackupStore;
import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.application.security.SecurityFindingStore;
import com.ivan.nexus.application.site.DomainStore;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupStatus;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.project.Project;
import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import com.ivan.nexus.domain.security.SecuritySeverity;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetProjectHealthTest {

    @Test
    void aggregatesAvailableSignals() {
        GetProject getProject = mock(GetProject.class);
        DeploymentStore deployments = mock(DeploymentStore.class);
        AlertStore alerts = mock(AlertStore.class);
        SecurityFindingStore findings = mock(SecurityFindingStore.class);
        BackupStore backups = mock(BackupStore.class);
        DomainStore domains = mock(DomainStore.class);

        when(getProject.execute("lab")).thenReturn(new GetProject.Result(
                new Project("lab", "lab", "HEALTHY", 2, 2, true),
                List.<ContainerSnapshot>of()));
        when(deployments.findProjectHistoryNewestFirst("lab")).thenReturn(List.of(
                deployment(DeploymentStatus.SUCCESS, Instant.parse("2026-01-02T00:00:00Z"))));
        when(alerts.latest(any())).thenReturn(List.of(
                alert("lab", AlertStatus.ACTIVE),
                alert("other", AlertStatus.ACTIVE),
                alert("lab", AlertStatus.ACKNOWLEDGED)));
        when(findings.listByProject("lab")).thenReturn(List.of(
                finding(SecurityFindingStatus.OPEN),
                finding(SecurityFindingStatus.RESOLVED),
                finding(SecurityFindingStatus.OPEN)));
        when(backups.findByProjectIdNewestFirst("lab")).thenReturn(List.of(
                backup(BackupStatus.FAILED, Instant.parse("2026-01-03T00:00:00Z")),
                backup(BackupStatus.SUCCESS, Instant.parse("2026-01-01T12:00:00Z"))));
        when(domains.findByProjectId("lab")).thenReturn(List.of(
                domain("app.example.com"),
                domain("www.example.com")));

        GetProjectHealth.Result result = new GetProjectHealth(
                getProject,
                deployments,
                alerts,
                Optional.of(findings),
                Optional.of(backups),
                Optional.of(domains))
                .execute("lab");

        assertThat(result.projectId()).isEqualTo("lab");
        assertThat(result.projectStatus()).isEqualTo("HEALTHY");
        assertThat(result.containersRunning()).isEqualTo(2);
        assertThat(result.containersTotal()).isEqualTo(2);
        assertThat(result.lastDeploymentStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(result.lastDeploymentAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(result.openAlertsCount()).isEqualTo(2);
        assertThat(result.openSecurityFindingsCount()).isEqualTo(2);
        assertThat(result.backupsAvailable()).isTrue();
        assertThat(result.lastBackupSuccessAt()).isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));
        assertThat(result.domainsCount()).isEqualTo(2);
    }

    @Test
    void degradesGracefullyWhenOptionalStoresMissing() {
        GetProject getProject = mock(GetProject.class);
        DeploymentStore deployments = mock(DeploymentStore.class);
        AlertStore alerts = mock(AlertStore.class);

        when(getProject.execute("lab")).thenReturn(new GetProject.Result(
                new Project("lab", "lab", "DOWN", 0, 1, true),
                List.of()));
        when(deployments.findProjectHistoryNewestFirst("lab")).thenReturn(List.of());
        when(alerts.latest(any())).thenReturn(List.of());

        GetProjectHealth.Result result = new GetProjectHealth(
                getProject,
                deployments,
                alerts,
                Optional.empty(),
                Optional.empty(),
                Optional.empty())
                .execute("lab");

        assertThat(result.containersRunning()).isZero();
        assertThat(result.containersTotal()).isEqualTo(1);
        assertThat(result.lastDeploymentStatus()).isNull();
        assertThat(result.lastDeploymentAt()).isNull();
        assertThat(result.openAlertsCount()).isZero();
        assertThat(result.openSecurityFindingsCount()).isNull();
        assertThat(result.backupsAvailable()).isFalse();
        assertThat(result.lastBackupSuccessAt()).isNull();
        assertThat(result.domainsCount()).isNull();
    }

    private static Deployment deployment(DeploymentStatus status, Instant finishedAt) {
        return new Deployment(
                UUID.randomUUID(),
                "lab",
                status,
                Instant.parse("2026-01-01T23:00:00Z"),
                finishedAt,
                "admin",
                "abc123",
                0,
                "ok",
                true,
                "deploy");
    }

    private static Alert alert(String projectId, AlertStatus status) {
        return new Alert(
                UUID.randomUUID(),
                UUID.randomUUID(),
                projectId,
                "api",
                status,
                "down",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                AlertType.CONTAINER_STOPPED);
    }

    private static SecurityFinding finding(SecurityFindingStatus status) {
        return new SecurityFinding(
                UUID.randomUUID(),
                "lab",
                SecuritySeverity.HIGH,
                "trivy",
                "openssl",
                "1.0.0",
                "1.0.1",
                "CVE",
                UUID.randomUUID().toString(),
                status,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static Backup backup(BackupStatus status, Instant finishedAt) {
        return new Backup(
                UUID.randomUUID(),
                "lab",
                status,
                BackupKind.SCHEDULED,
                Instant.parse("2026-01-01T00:00:00Z"),
                finishedAt,
                "s3://bucket/lab",
                "done",
                true,
                true);
    }

    private static SiteDomain domain(String hostname) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new SiteDomain(
                UUID.randomUUID(),
                "lab",
                hostname,
                "api",
                8080,
                now,
                now,
                CertStatus.ACTIVE);
    }
}
