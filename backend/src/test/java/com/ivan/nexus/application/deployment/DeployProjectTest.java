package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.sse.DeploymentStreamHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeployProjectTest {

    @TempDir
    Path allowedRoot;

    private final FakeManifestCatalog manifests = new FakeManifestCatalog();
    private final FakeProcessExecutor processExecutor = new FakeProcessExecutor();
    private final FakeHealthChecker healthChecker = new FakeHealthChecker();
    private final RecordAudit recordAudit = mock(RecordAudit.class);
    private final RecordActivity recordActivity = mock(RecordActivity.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final UUID adminId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final Map<UUID, List<String>> hubLines = new ConcurrentHashMap<>();

    private FakeManagedProjectStore projects;
    private FakeDeploymentStore deployments;
    private DeploymentStreamHub hub;
    private DeployProject useCase;

    @BeforeEach
    void setUp() {
        projects = new FakeManagedProjectStore();
        deployments = new FakeDeploymentStore();
        hub = mockHub();

        when(users.findIdByUsername("admin")).thenReturn(Optional.of(adminId));

        useCase = useCaseWithExecutor(Runnable::run);
    }

    @Test
    void successPathMarksSuccessHealthOkAndAudits() throws Exception {
        writeLabManifest("http://127.0.0.1:18080");
        processExecutor.exitCode = 0;
        processExecutor.lines = List.of("pulling repository", "building api");
        healthChecker.result = true;

        Deployment started = useCase.execute("lab", "admin");

        assertThat(started.status()).isEqualTo(DeploymentStatus.RUNNING);
        Deployment stored = deployments.deployments.get(started.id());
        assertThat(stored.status()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(stored.healthOk()).isTrue();
        assertThat(stored.exitCode()).isZero();
        assertThat(stored.finishedAt()).isNotNull();
        assertThat(hubLines.get(started.id())).contains(
                "deployment started",
                "pulling repository",
                "building api",
                "health check",
                "health check OK",
                "DEPLOYMENT SUCCESS");
        assertThat(healthChecker.called).isTrue();
        verify(recordActivity).execute(
                eq(ActivityType.DEPLOYMENT_STARTED),
                eq("lab"),
                isNull(),
                eq("deployment started"),
                any());
        verify(recordActivity).execute(
                eq(ActivityType.DEPLOYMENT_SUCCESS),
                eq("lab"),
                isNull(),
                eq("deployment successful"),
                any());
        verify(recordAudit).execute(
                eq(adminId),
                eq(AuditAction.DEPLOY),
                eq("lab"),
                isNull(),
                isNull(),
                any());
        verify(hub).complete(started.id());
        assertThat(deployments.calls).containsExactly(
                "active:lab",
                "pending:" + started.id(),
                "running:" + started.id(),
                "find:" + started.id(),
                "exit:0",
                "health:true",
                "finish:SUCCESS");
    }

    @Test
    void nonZeroExitMarksFailedWithoutHealthCheck() throws Exception {
        writeLabManifest("http://127.0.0.1:18080");
        processExecutor.exitCode = 1;
        processExecutor.lines = List.of("build error");

        Deployment started = useCase.execute("lab", "admin");

        Deployment stored = deployments.deployments.get(started.id());
        assertThat(stored.status()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(stored.healthOk()).isNull();
        assertThat(stored.exitCode()).isEqualTo(1);
        assertThat(hubLines.get(started.id())).contains("build error", "DEPLOYMENT FAILED");
        assertThat(healthChecker.called).isFalse();
    }

    @Test
    void healthFalseMarksFailedWithHealthOkFalse() throws Exception {
        writeLabManifest("http://127.0.0.1:18080");
        processExecutor.exitCode = 0;
        healthChecker.result = false;

        Deployment started = useCase.execute("lab", "admin");

        Deployment stored = deployments.deployments.get(started.id());
        assertThat(stored.status()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(stored.healthOk()).isFalse();
        assertThat(hubLines.get(started.id())).contains(
                "health check",
                "health check FAILED",
                "DEPLOYMENT FAILED");
        assertThat(healthChecker.called).isTrue();
        verify(recordActivity).execute(
                eq(ActivityType.HEALTH_CHECK_FAILED),
                eq("lab"),
                isNull(),
                eq("health check failed"),
                any());
        verify(recordActivity).execute(
                eq(ActivityType.DEPLOYMENT_FAILED),
                eq("lab"),
                isNull(),
                eq("deployment failed"),
                any());
    }

    @Test
    void secondConcurrentStartThrowsDeploymentInProgress() throws Exception {
        writeLabManifest(null);
        AtomicReference<Runnable> deferred = new AtomicReference<>();
        DeployProject deferredUseCase = useCaseWithExecutor(deferred::set);

        deferredUseCase.execute("lab", "admin");

        assertThatThrownBy(() -> deferredUseCase.execute("lab", "admin"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.DEPLOYMENT_IN_PROGRESS));
    }

    @Test
    void missingManifestThrowsManifestNotFound() {
        assertThatThrownBy(() -> useCase.execute("lab", "admin"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.MANIFEST_NOT_FOUND));
    }

    @Test
    void upsertsManagedProjectFromManifestAndKeepsLastSummary() throws Exception {
        writeLabManifest(null);
        processExecutor.exitCode = 0;
        processExecutor.lines = List.of("pulling repository");

        Deployment started = useCase.execute("lab", "admin");

        FakeManagedProjectStore.SavedProject stored = projects.projects.get("lab");
        assertThat(stored.name()).isEqualTo("Lab");
        assertThat(stored.description()).isEqualTo("Test fixture");
        assertThat(stored.workingDirectory())
                .isEqualTo(allowedRoot.resolve("lab").toAbsolutePath().normalize());
        assertThat(stored.manifestPath())
                .isEqualTo(allowedRoot.resolve("lab").resolve("nexus.yml").toAbsolutePath().normalize());
        assertThat(deployments.deployments.get(started.id()).outputSummary()).isEqualTo(
                "deployment started\npulling repository\nDEPLOYMENT SUCCESS");
    }

    @Test
    void blankProjectNameFallsBackToId() throws Exception {
        writeLabManifest(null);
        Path dir = allowedRoot.resolve("lab");
        manifests.add("lab", manifest("  ", dir, null), dir.resolve("nexus.yml"));

        useCase.execute("lab", "admin");

        assertThat(projects.projects.get("lab").name()).isEqualTo("lab");
    }

    private DeployProject useCaseWithExecutor(Executor executor) {
        return new DeployProject(
                manifests,
                projects,
                deployments,
                hub,
                processExecutor,
                healthChecker,
                recordAudit,
                recordActivity,
                users,
                executor);
    }

    private DeploymentStreamHub mockHub() {
        DeploymentStreamHub mockHub = mock(DeploymentStreamHub.class);
        doAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            String line = invocation.getArgument(1);
            hubLines.computeIfAbsent(id, key -> new CopyOnWriteArrayList<>()).add(line);
            return null;
        }).when(mockHub).append(any(), any());
        return mockHub;
    }

    private void writeLabManifest(String healthUrl) throws Exception {
        Path dir = allowedRoot.resolve("lab");
        Files.createDirectories(dir);
        Path script = dir.resolve("deploy.sh");
        Files.writeString(script, "#!/bin/sh\necho ok\n");
        assertThat(script.toFile().setExecutable(true, false)).isTrue();
        manifests.add("lab", manifest("Lab", dir, healthUrl), dir.resolve("nexus.yml"));
    }

    private static ProjectManifest manifest(String name, Path workingDirectory, String healthUrl) {
        ProjectManifest.HealthBlock health = healthUrl == null
                ? null
                : new ProjectManifest.HealthBlock(healthUrl, 5);
        return new ProjectManifest(
                new ProjectManifest.ProjectBlock(
                        "lab", name, "Test fixture", workingDirectory.toAbsolutePath().toString()),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                health,
                null);
    }

    static final class FakeProcessExecutor implements ProcessExecutor {
        int exitCode = 0;
        List<String> lines = List.of();

        @Override
        public int run(Path workingDirectory, List<String> argv, Consumer<String> onLine, Duration timeout) {
            lines.forEach(onLine);
            return exitCode;
        }
    }

    static final class FakeHealthChecker implements HealthChecker {
        boolean result = true;
        boolean called;

        @Override
        public boolean check(String url, Duration timeout) {
            called = true;
            return result;
        }
    }
}
