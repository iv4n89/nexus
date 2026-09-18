package com.ivan.nexus.application.env;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    RecordAudit recordAudit;
    @Mock
    RecordActivity recordActivity;
    @Mock
    UserDirectory users;

    private final FakeProjectDotEnvStore store = new FakeProjectDotEnvStore();
    private final UUID adminId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private UpsertProjectEnv upsert;
    private ListProjectEnv list;
    private DeleteProjectEnv delete;
    private RotateProjectEnv rotate;

    @BeforeEach
    void setUp() {
        upsert = new UpsertProjectEnv(store, recordAudit, recordActivity, users);
        list = new ListProjectEnv(store);
        delete = new DeleteProjectEnv(store, recordAudit, recordActivity, users);
        rotate = new RotateProjectEnv(store, recordAudit, recordActivity, users);
    }

    @Test
    void upsertWritesDotEnvAndNeverReturnsSecretPlaintext() {
        when(users.findIdByUsername("admin")).thenReturn(Optional.of(adminId));

        ProjectEnvVarView view = upsert.execute("lab", "API_KEY", "s3cret", true, "admin", "10.0.0.1");

        assertThat(view.secret()).isTrue();
        assertThat(view.value()).isNull();
        assertThat(store.read("lab").get("API_KEY")).isEqualTo("s3cret");

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
    void listMasksSecretsAndReturnsNonSecrets() {
        store.write("lab", Map.of("API_KEY", "s3cret", "NODE_ENV", "production"));

        List<ProjectEnvVarView> views = list.execute("lab");

        assertThat(views).hasSize(2);
        ProjectEnvVarView secret = views.stream().filter(ProjectEnvVarView::secret).findFirst().orElseThrow();
        ProjectEnvVarView plain = views.stream().filter(v -> !v.secret()).findFirst().orElseThrow();
        assertThat(secret.value()).isNull();
        assertThat(plain.value()).isEqualTo("production");
    }

    @Test
    void rotateRewritesDotEnvAndRecordsAudit() {
        store.write("lab", Map.of("API_KEY", "old-secret"));
        when(users.findIdByUsername("admin")).thenReturn(Optional.of(adminId));

        ProjectEnvVarView view = rotate.execute("lab", "API_KEY", "new-secret", "admin", "10.0.0.2");

        assertThat(view.secret()).isTrue();
        assertThat(view.value()).isNull();
        assertThat(view.id()).isEqualTo(UUID.nameUUIDFromBytes("lab:API_KEY".getBytes(StandardCharsets.UTF_8)));
        assertThat(store.read("lab").get("API_KEY")).isEqualTo("new-secret");

        verify(recordAudit).execute(
                eq(adminId),
                eq(AuditAction.CONFIG_CHANGE),
                eq("lab"),
                isNull(),
                eq("10.0.0.2"),
                any());
        verify(recordActivity).execute(
                eq(ActivityType.CONFIG_CHANGED),
                eq("lab"),
                isNull(),
                eq("Environment variable API_KEY rotated"),
                any());
    }

    @Test
    void rotateMissingThrowsNotFound() {
        assertThatThrownBy(() -> rotate.execute("lab", "MISSING", "x", "admin", null))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.ENV_VAR_NOT_FOUND));
    }

    @Test
    void deleteRecordsAuditAndActivity() {
        store.write("lab", Map.of("API_KEY", "cipher"));
        when(users.findIdByUsername("admin")).thenReturn(Optional.of(adminId));

        delete.execute("lab", "API_KEY", "admin", "127.0.0.1");

        assertThat(store.read("lab")).doesNotContainKey("API_KEY");
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

    static final class FakeProjectDotEnvStore implements ProjectDotEnvStore {
        private final ConcurrentHashMap<String, LinkedHashMap<String, String>> byProject =
                new ConcurrentHashMap<>();

        @Override
        public Map<String, String> read(String projectId) {
            return new LinkedHashMap<>(byProject.getOrDefault(projectId, new LinkedHashMap<>()));
        }

        @Override
        public void write(String projectId, Map<String, String> values) {
            byProject.put(projectId, new LinkedHashMap<>(values));
        }
    }
}
