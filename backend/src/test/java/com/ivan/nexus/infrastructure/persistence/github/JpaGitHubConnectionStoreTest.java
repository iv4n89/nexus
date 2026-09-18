package com.ivan.nexus.infrastructure.persistence.github;

import com.ivan.nexus.application.github.StoredGitHubConnection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaGitHubConnectionStoreTest {

    @Test
    void mapsEntityToStoredConnection() {
        GitHubConnectionJpaRepository repository = mock(GitHubConnectionJpaRepository.class);
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Instant connectedAt = Instant.parse("2026-09-18T08:00:00Z");
        Instant updatedAt = Instant.parse("2026-09-18T08:05:00Z");
        when(repository.findAll()).thenReturn(List.of(new GitHubConnectionEntity(
                id, "octocat", "enc-token", "enc-refresh", "repo", connectedAt, updatedAt)));

        Optional<StoredGitHubConnection> loaded = new JpaGitHubConnectionStore(repository).load();

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().githubLogin()).isEqualTo("octocat");
        assertThat(loaded.orElseThrow().encryptedToken()).isEqualTo("enc-token");
        assertThat(loaded.orElseThrow().encryptedRefreshToken()).isEqualTo("enc-refresh");
    }

    @Test
    void savesNewConnection() {
        GitHubConnectionJpaRepository repository = mock(GitHubConnectionJpaRepository.class);
        when(repository.findAll()).thenReturn(List.of());
        Instant now = Instant.parse("2026-09-18T08:00:00Z");
        UUID id = UUID.randomUUID();
        StoredGitHubConnection connection = new StoredGitHubConnection(
                id, "octocat", "enc", null, "repo", now, now);

        new JpaGitHubConnectionStore(repository).save(connection);

        ArgumentCaptor<GitHubConnectionEntity> captor = ArgumentCaptor.forClass(GitHubConnectionEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedToken()).isEqualTo("enc");
        assertThat(captor.getValue().getGithubLogin()).isEqualTo("octocat");
    }
}
