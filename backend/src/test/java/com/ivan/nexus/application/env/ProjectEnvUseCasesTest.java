package com.ivan.nexus.application.env;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.secrets.SecretStore;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectEnvUseCasesTest {

    @Mock
    SecretStore secretStore;
    @Mock
    RecordAudit recordAudit;
    @Mock
    RecordActivity recordActivity;
    @Mock
    UserDirectory users;

    private final FakeProjectEnvStore store = new FakeProjectEnvStore();
    private final UUID adminId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private UpsertProjectEnv upsert;
    private ListProjectEnv list;
    private DeleteProjectEnv delete;

    @BeforeEach
    void setUp() {
        upsert = new UpsertProjectEnv(store, secretStore, recordAudit, recordActivity, users);
        list = new ListProjectEnv(store, secretStore);
        delete = new DeleteProjectEnv(store, recordAudit, recordActivity, users);
    }

    @Test
    void upsertEncryptsAndNeverReturnsSecretPlaintext() {
        when(secretStore.encrypt("s3cret")).thenReturn("cipher");
        when(users.findIdByUsername("admin")).thenReturn(Optional.of(adminId));

        ProjectEnvVarView view = upsert.execute("lab", "API_KEY", "s3cret", true, "admin", "10.0.0.1");

        assertThat(view.secret()).isTrue();
        assertThat(view.value()).isNull();
        assertThat(store.findByProjectAndName("lab", "API_KEY").orElseThrow().encryptedValue())
                .isEqualTo("cipher");
        assertThat(store.findByProjectAndName("lab", "API_KEY").orElseThrow().encryptedValue())
                .doesNotContain("s3cret");

        verify(recordAudit).execute(
                eq(adminId),
                eq(AuditAction.CONFIG_CHANGE),
                eq("lab"),
                isNull(),
                eq("10.0.0.1"),
                any());
        verify(recordActivity).execute(
                eq(ActivityType.CONFIG_CHANGED),
                eq("lab"),
                isNull(),
                eq("Environment variable API_KEY created"),
                any());
    }

    @Test
    void listMasksSecretsAndDecryptsNonSecrets() {
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        store.upsert(new StoredProjectEnvVar(
                UUID.randomUUID(), "lab", "API_KEY", "cipher-secret", true, now, now));
        store.upsert(new StoredProjectEnvVar(
                UUID.randomUUID(), "lab", "NODE_ENV", "cipher-prod", false, now, now));
        when(secretStore.decrypt("cipher-prod")).thenReturn("production");

        List<ProjectEnvVarView> views = list.execute("lab");

        assertThat(views).hasSize(2);
        ProjectEnvVarView secret = views.stream().filter(ProjectEnvVarView::secret).findFirst().orElseThrow();
        ProjectEnvVarView plain = views.stream().filter(v -> !v.secret()).findFirst().orElseThrow();
        assertThat(secret.value()).isNull();
        assertThat(plain.value()).isEqualTo("production");
    }

    @Test
    void deleteRecordsAuditAndActivity() {
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        store.upsert(new StoredProjectEnvVar(
                UUID.randomUUID(), "lab", "API_KEY", "cipher", true, now, now));
        when(users.findIdByUsername("admin")).thenReturn(Optional.of(adminId));

        delete.execute("lab", "API_KEY", "admin", "127.0.0.1");

        assertThat(store.findByProjectAndName("lab", "API_KEY")).isEmpty();
        verify(recordAudit).execute(
                eq(adminId),
                eq(AuditAction.CONFIG_CHANGE),
                eq("lab"),
                isNull(),
                eq("127.0.0.1"),
                any());
        verify(recordActivity).execute(
                eq(ActivityType.CONFIG_CHANGED),
                eq("lab"),
                isNull(),
                eq("Environment variable API_KEY deleted"),
                any());
    }

    @Test
    void deleteMissingThrowsNotFound() {
        assertThatThrownBy(() -> delete.execute("lab", "MISSING", "admin", null))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.ENV_VAR_NOT_FOUND));
    }

    @Test
    void rejectsInvalidName() {
        assertThatThrownBy(() -> upsert.execute("lab", "bad-name", "x", false, "admin", null))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.OPERATION_NOT_ALLOWED));
    }

    static final class FakeProjectEnvStore implements ProjectEnvStore {
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<StoredProjectEnvVar>> byProject =
                new ConcurrentHashMap<>();

        @Override
        public List<StoredProjectEnvVar> listByProject(String projectId) {
            return List.copyOf(byProject.getOrDefault(projectId, new CopyOnWriteArrayList<>()));
        }

        @Override
        public Optional<StoredProjectEnvVar> findByProjectAndName(String projectId, String name) {
            return listByProject(projectId).stream().filter(v -> v.name().equals(name)).findFirst();
        }

        @Override
        public Optional<StoredProjectEnvVar> findById(UUID id) {
            return byProject.values().stream().flatMap(List::stream).filter(v -> v.id().equals(id)).findFirst();
        }

        @Override
        public StoredProjectEnvVar upsert(StoredProjectEnvVar envVar) {
            CopyOnWriteArrayList<StoredProjectEnvVar> list =
                    byProject.computeIfAbsent(envVar.projectId(), key -> new CopyOnWriteArrayList<>());
            list.removeIf(v -> v.name().equals(envVar.name()));
            list.add(envVar);
            return envVar;
        }

        @Override
        public void delete(String projectId, String name) {
            byProject.computeIfPresent(projectId, (key, list) -> {
                list.removeIf(v -> v.name().equals(name));
                return list;
            });
        }
    }
}
