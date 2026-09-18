package com.ivan.nexus.application.github;

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

import java.time.Instant;
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
class DisconnectGitHubTest {

    @Mock
    RecordAudit recordAudit;
    @Mock
    RecordActivity recordActivity;
    @Mock
    UserDirectory users;

    private final FakeStore store = new FakeStore();
    private final UUID adminId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private DisconnectGitHub useCase;

    @BeforeEach
    void setUp() {
        useCase = new DisconnectGitHub(store, recordAudit, recordActivity, users);
    }

    @Test
    void throwsWhenNotConnected() {
        assertThatThrownBy(() -> useCase.execute("admin", "127.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.GITHUB_NOT_CONNECTED));
        verify(recordAudit, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void clearsAndAudits() {
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        store.save(new StoredGitHubConnection(
                UUID.randomUUID(), "octocat", "cipher", null, "repo", now, now));
        when(users.findIdByUsername("admin")).thenReturn(Optional.of(adminId));

        useCase.execute("admin", "127.0.0.1");

        assertThat(store.load()).isEmpty();
        verify(recordAudit).execute(
                eq(adminId),
                eq(AuditAction.GITHUB_DISCONNECT),
                isNull(),
                isNull(),
                eq("127.0.0.1"),
                any());
        verify(recordActivity).execute(
                eq(ActivityType.GITHUB_DISCONNECTED),
                isNull(),
                isNull(),
                eq("GitHub disconnected (octocat)"),
                any());
    }

    static final class FakeStore implements GitHubConnectionStore {
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
