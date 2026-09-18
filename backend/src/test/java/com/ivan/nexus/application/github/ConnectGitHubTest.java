package com.ivan.nexus.application.github;

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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectGitHubTest {

    @Mock
    GitHubClient gitHubClient;
    @Mock
    GitHubOAuthStateStore stateStore;
    @Mock
    SecretStore secretStore;
    @Mock
    RecordAudit recordAudit;
    @Mock
    RecordActivity recordActivity;
    @Mock
    UserDirectory users;

    private final FakeGitHubConnectionStore connectionStore = new FakeGitHubConnectionStore();
    private final UUID adminId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private ConnectGitHub useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConnectGitHub(
                gitHubClient,
                stateStore,
                connectionStore,
                secretStore,
                recordAudit,
                recordActivity,
                users);
    }

    @Test
    void rejectsInvalidState() {
        when(stateStore.consume("bad")).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute("code", "bad", "admin", "127.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.GITHUB_OAUTH_STATE_INVALID));

        verify(gitHubClient, never()).exchangeCode(any());
        assertThat(connectionStore.load()).isEmpty();
    }

    @Test
    void encryptsTokenAndRecordsAuditActivity() {
        when(stateStore.consume("state-1")).thenReturn(true);
        when(gitHubClient.exchangeCode("code-1")).thenReturn(
                new GitHubOAuthToken("gho_plain", null, "repo,read:user", "bearer"));
        when(gitHubClient.getAuthenticatedUser("gho_plain")).thenReturn(new GitHubIdentity("octocat", 1L));
        when(secretStore.encrypt("gho_plain")).thenReturn("cipher-token");
        when(users.findIdByUsername("admin")).thenReturn(Optional.of(adminId));

        GitHubConnectionView view = useCase.execute("code-1", "state-1", "admin", "10.0.0.1");

        assertThat(view.connected()).isTrue();
        assertThat(view.githubLogin()).isEqualTo("octocat");
        assertThat(view.scopes()).isEqualTo("repo,read:user");

        StoredGitHubConnection stored = connectionStore.load().orElseThrow();
        assertThat(stored.encryptedToken()).isEqualTo("cipher-token");
        assertThat(stored.encryptedToken()).doesNotContain("gho_plain");
        assertThat(stored.encryptedRefreshToken()).isNull();

        verify(recordAudit).execute(
                eq(adminId),
                eq(AuditAction.GITHUB_CONNECT),
                isNull(),
                isNull(),
                eq("10.0.0.1"),
                any());
        verify(recordActivity).execute(
                eq(ActivityType.GITHUB_CONNECTED),
                isNull(),
                isNull(),
                eq("GitHub connected as octocat"),
                any());
    }

    @Test
    void encryptsRefreshTokenWhenPresent() {
        when(stateStore.consume("state-2")).thenReturn(true);
        when(gitHubClient.exchangeCode("code-2")).thenReturn(
                new GitHubOAuthToken("access", "refresh", "repo", "bearer"));
        when(gitHubClient.getAuthenticatedUser("access")).thenReturn(new GitHubIdentity("octocat", 1L));
        when(secretStore.encrypt("access")).thenReturn("enc-access");
        when(secretStore.encrypt("refresh")).thenReturn("enc-refresh");
        when(users.findIdByUsername("admin")).thenReturn(Optional.of(adminId));

        useCase.execute("code-2", "state-2", "admin", null);

        assertThat(connectionStore.load()).isPresent();
        assertThat(connectionStore.load().orElseThrow().encryptedRefreshToken()).isEqualTo("enc-refresh");
        verify(secretStore).encrypt("refresh");
    }

    static final class FakeGitHubConnectionStore implements GitHubConnectionStore {
        private final AtomicReference<StoredGitHubConnection> current = new AtomicReference<>();

        @Override
        public Optional<StoredGitHubConnection> load() {
            return Optional.ofNullable(current.get());
        }

        @Override
        public void save(StoredGitHubConnection connection) {
            current.set(connection);
        }

        @Override
        public void clear() {
            current.set(null);
        }
    }
}
