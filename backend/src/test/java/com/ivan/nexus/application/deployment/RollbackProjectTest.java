package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.env.ProjectEnvStore;
import com.ivan.nexus.application.env.StoredProjectEnvVar;
import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import com.ivan.nexus.application.secrets.SecretStore;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RollbackProjectTest {

    @TempDir
    Path allowedRoot;

    private final FakeManifestCatalog manifests = new FakeManifestCatalog();
    private final FakeProcessExecutor processExecutor = new FakeProcessExecutor();
    private final FakeHealthChecker healthChecker = new FakeHealthChecker();
    private final FakeProjectEnvStore projectEnvStore = new FakeProjectEnvStore();
    private final SecretStore secretStore = new IdentitySecretStore();
    private final RecordAudit recordAudit = mock(RecordAudit.class);
    private final RecordActivity recordActivity = mock(RecordActivity.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final UUID adminId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final Map<UUID, List<String>> hubLines = new ConcurrentHashMap<>();

    private FakeManagedProjectStore projects;
    private FakeDeploymentStore deployments;
    private DeploymentProgress progress;
    private RollbackProject useCase;

    @BeforeEach
    void setUp() {
        projects = new FakeManagedProjectStore();
        deployments = new FakeDeploymentStore();
        progress = mockProgress();

        when(users.findIdByUsername("admin")).thenReturn(Optional.of(adminId));

        useCase = useCaseWithExecutor(Runnable::run);
    }

    @Test
    void missingRollbackCommandThrowsOperationNotAllowed() throws Exception {
        writeLabManifest(null, "http://127.0.0.1:18080");

        assertThatThrownBy(() -> useCase.execute("lab", "admin"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.OPERATION_NOT_ALLOWED));
        assertThat(deployments.deployments).isEmpty();
    }

    @Test
    void blankRollbackCommandThrowsOperationNotAllowed() throws Exception {
        writeLabManifest("   ", "http://127.0.0.1:18080");

        assertThatThrownBy(() -> useCase.execute("lab", "admin"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.OPERATION_NOT_ALLOWED));
        assertThat(deployments.deployments).isEmpty();
    }

    @Test
    void successPathMarksSuccessHealthOkAndAuditsRollback() throws Exception {
        writeLabManifest("./rollback.sh", "http://127.0.0.1:18080");
        processExecutor.exitCode = 0;
        processExecutor.lines = List.of("rolling back");
        healthChecker.result = true;

        Deployment started = useCase.execute("lab", "admin");

        assertThat(started.status()).isEqualTo(DeploymentStatus.RUNNING);
        Deployment stored = deployments.deployments.get(started.id());
        assertThat(stored.status()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(stored.healthOk()).isTrue();
        assertThat(stored.exitCode()).isZero();
        assertThat(stored.finishedAt()).isNotNull();
        assertThat(stored.kind()).isEqualTo("rollback");
        assertThat(processExecutor.lastArgv.getFirst()).endsWith("rollback.sh");
        assertThat(hubLines.get(started.id())).contains(
                "rollback started",
                "rolling back",
                "health check",
                "health check OK",
                "DEPLOYMENT SUCCESS");
        assertThat(healthChecker.called).isTrue();
        verify(recordActivity).execute(
                eq(ActivityType.DEPLOYMENT_STARTED),
                eq("lab"),
                isNull(),
                eq("rollback started"),
                argThat(meta -> "rollback".equals(meta.get("kind"))));
        verify(recordActivity).execute(
                eq(ActivityType.DEPLOYMENT_SUCCESS),
                eq("lab"),
                isNull(),
                eq("rollback successful"),
                argThat(meta -> "rollback".equals(meta.get("kind"))));
        verify(recordAudit).execute(
                eq(adminId),
                eq(AuditAction.ROLLBACK),
                eq("lab"),
                isNull(),
                isNull(),
                any());
        verify(progress).complete(started.id());
    }

    @Test
    void secondConcurrentStartThrowsDeploymentInProgress() throws Exception {
        writeLabManifest("./rollback.sh", null);
        AtomicReference<Runnable> deferred = new AtomicReference<>();
        RollbackProject deferredUseCase = useCaseWithExecutor(deferred::set);

        deferredUseCase.execute("lab", "admin");

        assertThatThrownBy(() -> deferredUseCase.execute("lab", "admin"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.DEPLOYMENT_IN_PROGRESS));
    }

    private RollbackProject useCaseWithExecutor(Executor executor) {
        return new RollbackProject(
                manifests,
                projects,
                deployments,
                progress,
                processExecutor,
                healthChecker,
                projectEnvStore,
                secretStore,
                recordAudit,
                recordActivity,
                users,
                executor);
    }

    private DeploymentProgress mockProgress() {
        DeploymentProgress mockProgress = mock(DeploymentProgress.class);
        doAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            String line = invocation.getArgument(1);
            hubLines.computeIfAbsent(id, key -> new CopyOnWriteArrayList<>()).add(line);
            return null;
        }).when(mockProgress).append(any(), any());
        return mockProgress;
    }

    private void writeLabManifest(String rollbackCommand, String healthUrl) throws Exception {
        Path dir = allowedRoot.resolve("lab");
        Files.createDirectories(dir);
        Path deploy = dir.resolve("deploy.sh");
        Files.writeString(deploy, "#!/bin/sh\necho deploy\n");
        assertThat(deploy.toFile().setExecutable(true, false)).isTrue();
        Path rollback = dir.resolve("rollback.sh");
        Files.writeString(rollback, "#!/bin/sh\necho rollback\n");
        assertThat(rollback.toFile().setExecutable(true, false)).isTrue();
        ProjectManifest.CommandBlock rollbackCommandBlock = rollbackCommand == null
                ? null
                : new ProjectManifest.CommandBlock(rollbackCommand);
        ProjectManifest.HealthBlock health = healthUrl == null
                ? null
                : new ProjectManifest.HealthBlock(healthUrl, 5);
        ProjectManifest manifest = new ProjectManifest(
                new ProjectManifest.ProjectBlock(
                        "lab", "Lab", "Test fixture", dir.toAbsolutePath().toString()),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                rollbackCommandBlock,
                health,
                null);
        manifests.add("lab", manifest, dir.resolve("nexus.yml"));
    }

    static final class FakeProcessExecutor implements ProcessExecutor {
        int exitCode = 0;
        List<String> lines = List.of();
        List<String> lastArgv = List.of();
        Map<String, String> lastEnvironment = Map.of();

        @Override
        public int run(
                Path workingDirectory,
                List<String> argv,
                Map<String, String> environment,
                Consumer<String> onLine,
                Duration timeout) {
            lastArgv = argv;
            lastEnvironment = environment == null ? Map.of() : Map.copyOf(environment);
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

    static final class FakeProjectEnvStore implements ProjectEnvStore {
        @Override
        public List<StoredProjectEnvVar> listByProject(String projectId) {
            return List.of();
        }

        @Override
        public Optional<StoredProjectEnvVar> findByProjectAndName(String projectId, String name) {
            return Optional.empty();
        }

        @Override
        public Optional<StoredProjectEnvVar> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public StoredProjectEnvVar upsert(StoredProjectEnvVar envVar) {
            return envVar;
        }

        @Override
        public void delete(String projectId, String name) {
        }
    }

    static final class IdentitySecretStore implements SecretStore {
        @Override
        public String encrypt(String plaintext) {
            return plaintext;
        }

        @Override
        public String decrypt(String ciphertext) {
            return ciphertext;
        }
    }
}
