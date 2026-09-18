package com.ivan.nexus.application.github;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GetGitHubConnectionTest {

    @Test
    void returnsDisconnectedWhenEmpty() {
        FakeStore store = new FakeStore();
        GitHubConnectionView view = new GetGitHubConnection(store).execute();
        assertThat(view.connected()).isFalse();
        assertThat(view.githubLogin()).isNull();
    }

    @Test
    void returnsStatusWithoutExposingCiphertext() {
        FakeStore store = new FakeStore();
        Instant connectedAt = Instant.parse("2026-09-18T09:00:00Z");
        store.save(new StoredGitHubConnection(
                UUID.randomUUID(), "octocat", "cipher-token", "cipher-refresh", "repo", connectedAt, connectedAt));

        GitHubConnectionView view = new GetGitHubConnection(store).execute();

        assertThat(view.connected()).isTrue();
        assertThat(view.githubLogin()).isEqualTo("octocat");
        assertThat(view.scopes()).isEqualTo("repo");
        assertThat(view.connectedAt()).isEqualTo(connectedAt);
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
