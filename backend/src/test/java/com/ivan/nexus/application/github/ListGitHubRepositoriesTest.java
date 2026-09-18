package com.ivan.nexus.application.github;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListGitHubRepositoriesTest {

    @Test
    void listsRepositoriesWithDecryptedToken() {
        RequireGitHubAccessToken accessToken = mock(RequireGitHubAccessToken.class);
        GitHubClient client = mock(GitHubClient.class);
        when(accessToken.execute()).thenReturn("gho_token");
        when(client.listRepositories("gho_token")).thenReturn(List.of(
                new GitHubRepositorySummary(7L, "acme/app", "app", "acme", "main", true)));

        List<GitHubRepositorySummary> result = new ListGitHubRepositories(accessToken, client).execute();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().fullName()).isEqualTo("acme/app");
        verify(client).listRepositories("gho_token");
    }

    @Test
    void requireTokenFailsWhenDisconnected() {
        GitHubConnectionStore store = mock(GitHubConnectionStore.class);
        com.ivan.nexus.application.secrets.SecretStore secrets =
                mock(com.ivan.nexus.application.secrets.SecretStore.class);
        when(store.load()).thenReturn(Optional.empty());
        RequireGitHubAccessToken require = new RequireGitHubAccessToken(store, secrets);

        assertThatThrownBy(require::execute)
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.GITHUB_NOT_CONNECTED);
    }

    @Test
    void requireTokenDecryptsStoredCiphertext() {
        UUID id = UUID.randomUUID();
        GitHubConnectionStore store = mock(GitHubConnectionStore.class);
        com.ivan.nexus.application.secrets.SecretStore secrets =
                mock(com.ivan.nexus.application.secrets.SecretStore.class);
        when(store.load()).thenReturn(Optional.of(new StoredGitHubConnection(
                id, "octocat", "cipher", null, "repo", Instant.now(), Instant.now())));
        when(secrets.decrypt("cipher")).thenReturn("plain-token");
        RequireGitHubAccessToken require = new RequireGitHubAccessToken(store, secrets);

        assertThat(require.execute()).isEqualTo("plain-token");
    }
}
