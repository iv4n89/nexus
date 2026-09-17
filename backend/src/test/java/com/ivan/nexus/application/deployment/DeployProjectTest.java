package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.manifest.YamlManifestLoader;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEntity;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import com.ivan.nexus.infrastructure.persistence.project.ManagedProjectEntity;
import com.ivan.nexus.infrastructure.persistence.project.ManagedProjectJpaRepository;
import com.ivan.nexus.infrastructure.persistence.user.UserEntity;
import com.ivan.nexus.infrastructure.persistence.user.UserJpaRepository;
import com.ivan.nexus.infrastructure.sse.DeploymentStreamHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
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

    private final YamlManifestLoader loader = new YamlManifestLoader();
    private final FakeProcessExecutor processExecutor = new FakeProcessExecutor();
    private final FakeHealthChecker healthChecker = new FakeHealthChecker();
    private final RecordAudit recordAudit = mock(RecordAudit.class);
    private final RecordActivity recordActivity = mock(RecordActivity.class);
    private final UserJpaRepository users = mock(UserJpaRepository.class);
    private final UUID adminId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final Map<String, ManagedProjectEntity> projectStore = new ConcurrentHashMap<>();
    private final Map<UUID, DeploymentEntity> deploymentStore = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> hubLines = new ConcurrentHashMap<>();

    private ManagedProjectJpaRepository projects;
    private DeploymentJpaRepository deployments;
    private DeploymentStreamHub hub;
    private DeployProject useCase;

    @BeforeEach
    void setUp() {
        projects = mockProjects();
        deployments = mockDeployments();
        hub = mockHub();

        UserEntity admin = mock(UserEntity.class);
        when(admin.getId()).thenReturn(adminId);
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));

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
        DeploymentEntity stored = deploymentStore.get(started.id());
        assertThat(stored.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(stored.getHealthOk()).isTrue();
        assertThat(stored.getExitCode()).isZero();
        assertThat(stored.getFinishedAt()).isNotNull();
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
    }

    @Test
    void nonZeroExitMarksFailedWithoutHealthCheck() throws Exception {
        writeLabManifest("http://127.0.0.1:18080");
        processExecutor.exitCode = 1;
        processExecutor.lines = List.of("build error");

        Deployment started = useCase.execute("lab", "admin");

        DeploymentEntity stored = deploymentStore.get(started.id());
        assertThat(stored.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(stored.getHealthOk()).isNull();
        assertThat(stored.getExitCode()).isEqualTo(1);
        assertThat(hubLines.get(started.id())).contains("build error", "DEPLOYMENT FAILED");
        assertThat(healthChecker.called).isFalse();
    }

    @Test
    void healthFalseMarksFailedWithHealthOkFalse() throws Exception {
        writeLabManifest("http://127.0.0.1:18080");
        processExecutor.exitCode = 0;
        healthChecker.result = false;

        Deployment started = useCase.execute("lab", "admin");

        DeploymentEntity stored = deploymentStore.get(started.id());
        assertThat(stored.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(stored.getHealthOk()).isFalse();
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

        ManagedProjectEntity stored = projectStore.get("lab");
        assertThat(stored.getName()).isEqualTo("Lab");
        assertThat(stored.getDescription()).isEqualTo("Test fixture");
        assertThat(stored.getWorkingDirectory())
                .isEqualTo(allowedRoot.resolve("lab").toAbsolutePath().normalize().toString());
        assertThat(stored.getManifestPath())
                .isEqualTo(allowedRoot.resolve("lab").resolve("nexus.yml").toAbsolutePath().normalize().toString());
        assertThat(deploymentStore.get(started.id()).getOutputSummary()).isEqualTo(
                "deployment started\npulling repository\nDEPLOYMENT SUCCESS");
    }

    @Test
    void blankProjectNameFallsBackToId() throws Exception {
        writeLabManifest(null);
        Path dir = allowedRoot.resolve("lab");
        Files.writeString(dir.resolve("nexus.yml"), """
                project:
                  id: lab
                  name: "  "
                  workingDirectory: %s
                deployment:
                  command: ./deploy.sh
                """.formatted(dir.toAbsolutePath()));

        useCase.execute("lab", "admin");

        assertThat(projectStore.get("lab").getName()).isEqualTo("lab");
    }

    private DeployProject useCaseWithExecutor(Executor executor) {
        return new DeployProject(
                loader,
                allowedRoot,
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

    private ManagedProjectJpaRepository mockProjects() {
        ManagedProjectJpaRepository repo = mock(ManagedProjectJpaRepository.class);
        when(repo.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(projectStore.get(invocation.getArgument(0))));
        when(repo.save(any())).thenAnswer(invocation -> {
            ManagedProjectEntity entity = invocation.getArgument(0);
            projectStore.put(entity.getId(), entity);
            return entity;
        });
        return repo;
    }

    private DeploymentJpaRepository mockDeployments() {
        DeploymentJpaRepository repo = mock(DeploymentJpaRepository.class);
        when(repo.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(deploymentStore.get(invocation.getArgument(0))));
        when(repo.save(any())).thenAnswer(invocation -> {
            DeploymentEntity entity = invocation.getArgument(0);
            deploymentStore.put(entity.getId(), entity);
            return entity;
        });
        when(repo.existsByProjectIdAndStatusIn(any(), any())).thenAnswer(invocation -> {
            String projectId = invocation.getArgument(0);
            Collection<DeploymentStatus> statuses = invocation.getArgument(1);
            return deploymentStore.values().stream()
                    .anyMatch(entity -> entity.getProjectId().equals(projectId)
                            && statuses.contains(entity.getStatus()));
        });
        return repo;
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
        String health = healthUrl == null
                ? ""
                : """
                health:
                  url: %s
                  timeoutSeconds: 5
                """.formatted(healthUrl);
        Files.writeString(dir.resolve("nexus.yml"), """
                project:
                  id: lab
                  name: Lab
                  description: Test fixture
                  workingDirectory: %s
                deployment:
                  command: ./deploy.sh
                %s
                """.formatted(dir.toAbsolutePath(), health));
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
