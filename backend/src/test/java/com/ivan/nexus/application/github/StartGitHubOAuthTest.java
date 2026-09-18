package com.ivan.nexus.application.github;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartGitHubOAuthTest {

    @Mock
    GitHubClient gitHubClient;
    @Mock
    GitHubOAuthStateStore stateStore;

    @Test
    void returnsAuthorizeUrl() {
        when(stateStore.issue()).thenReturn("state-abc");
        when(gitHubClient.buildAuthorizeUrl("state-abc"))
                .thenReturn("https://github.com/login/oauth/authorize?state=state-abc");

        String url = new StartGitHubOAuth(gitHubClient, stateStore).execute();

        assertThat(url).contains("github.com/login/oauth/authorize");
        assertThat(url).contains("state-abc");
    }

    @Test
    void mapsMissingConfigToDomainError() {
        when(stateStore.issue()).thenReturn("state");
        when(gitHubClient.buildAuthorizeUrl("state"))
                .thenThrow(new IllegalStateException("GitHub OAuth is not configured"));

        assertThatThrownBy(() -> new StartGitHubOAuth(gitHubClient, stateStore).execute())
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.GITHUB_NOT_CONFIGURED));
    }
}
