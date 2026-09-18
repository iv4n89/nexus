package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentCommandRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsStateAndSideEffectsInExecutionOrder() throws Exception {
        ManagedProjectStore projects = mock(ManagedProjectStore.class);
        DeploymentStore deployments = mock(DeploymentStore.class);
        DeploymentProgress progress = mock(DeploymentProgress.class);
        ProcessExecutor process = mock(ProcessExecutor.class);
        HealthChecker health = mock(HealthChecker.class);
        RecordAudit audit = mock(RecordAudit.class);
        RecordActivity activity = mock(RecordActivity.class);
        UserDirectory users = mock(UserDirectory.class);
        ArgumentCaptor<Runnable> async = ArgumentCaptor.forClass(Runnable.class);
        Executor executor = mock(Executor.class);
        UUID userId = UUID.randomUUID();
        Path script = tempDir.resolve("deploy.sh");
        Files.writeString(script, "#!/bin/sh\n");
        assertThat(script.toFile().setExecutable(true, false)).isTrue();
        ProjectManifest manifest = manifest(tempDir);

        when(deployments.hasActiveDeployment("lab")).thenReturn(false);
        when(deployments.createPending(any(), eq("lab"), eq("admin"), eq("deploy"), isNull()))
                .thenAnswer(invocation -> deployment(
                        invocation.getArgument(0), DeploymentStatus.PENDING, null));
        when(deployments.markRunning(any(), any()))
                .thenAnswer(invocation -> deployment(
                        invocation.getArgument(0), DeploymentStatus.RUNNING, invocation.getArgument(1)));
        when(deployments.findById(any()))
                .thenAnswer(invocation -> Optional.of(deployment(
                        invocation.getArgument(0), DeploymentStatus.RUNNING, Instant.now())));
        when(process.run(any(), any(), any(), any())).thenReturn(0);
        when(users.findIdByUsername("admin")).thenReturn(Optional.of(userId));

        DeploymentCommandRunner runner = new DeploymentCommandRunner(
                projects, deployments, progress, process, health, audit, activity, users, executor);

        Deployment started = runner.start(
                "lab",
                "admin",
                manifest,
                tempDir.resolve(".").resolve("nexus.yml"),
                "./deploy.sh",
                "deploy",
                AuditAction.DEPLOY,
                null);
        org.mockito.Mockito.verify(executor).execute(async.capture());
        async.getValue().run();

        var order = inOrder(projects, deployments, progress, process, activity, users, audit, executor);
        order.verify(deployments).hasActiveDeployment("lab");
        order.verify(projects).upsert(eq(manifest), eq(tempDir.toAbsolutePath().normalize()),
                eq(tempDir.resolve("nexus.yml").toAbsolutePath().normalize()));
        order.verify(deployments).createPending(started.id(), "lab", "admin", "deploy", null);
        order.verify(deployments).markRunning(eq(started.id()), any());
        order.verify(executor).execute(any());
        order.verify(deployments).findById(started.id());
        order.verify(progress).append(started.id(), "deployment started");
        order.verify(activity).execute(
                eq(ActivityType.DEPLOYMENT_STARTED), eq("lab"), isNull(),
                eq("deployment started"), any());
        order.verify(process).run(
                eq(tempDir.toAbsolutePath().normalize()), any(), any(),
                eq(DeploymentCommandRunner.SCRIPT_TIMEOUT));
        order.verify(deployments).recordExitCode(started.id(), 0);
        order.verify(progress).append(started.id(), "DEPLOYMENT SUCCESS");
        order.verify(activity).execute(
                eq(ActivityType.DEPLOYMENT_SUCCESS), eq("lab"), isNull(),
                eq("deployment successful"), any());
        order.verify(deployments).finish(eq(started.id()), eq(DeploymentStatus.SUCCESS), any(),
                eq("deployment started\nDEPLOYMENT SUCCESS"));
        order.verify(progress).complete(started.id());
        order.verify(users).findIdByUsername("admin");
        order.verify(audit).execute(
                eq(userId), eq(AuditAction.DEPLOY), eq("lab"), isNull(), isNull(), any());
    }

    private static ProjectManifest manifest(Path workingDirectory) {
        return new ProjectManifest(
                new ProjectManifest.ProjectBlock(
                        "lab", "Lab", null, workingDirectory.toAbsolutePath().toString()),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);
    }

    private static Deployment deployment(UUID id, DeploymentStatus status, Instant startedAt) {
        return new Deployment(
                id, "lab", status, startedAt, null, "admin",
                null, null, null, null, "deploy");
    }
}
