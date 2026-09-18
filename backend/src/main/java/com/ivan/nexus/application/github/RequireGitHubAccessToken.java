package com.ivan.nexus.application.github;

import com.ivan.nexus.application.secrets.SecretStore;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Component;

@Component
public class RequireGitHubAccessToken {
    private final GitHubConnectionStore connectionStore;
    private final SecretStore secretStore;

    public RequireGitHubAccessToken(GitHubConnectionStore connectionStore, SecretStore secretStore) {
        this.connectionStore = connectionStore;
        this.secretStore = secretStore;
    }

    public String execute() {
        StoredGitHubConnection connection = connectionStore.load()
                .orElseThrow(() -> new DomainException(
                        NexusErrorCode.GITHUB_NOT_CONNECTED, "GitHub is not connected"));
        return secretStore.decrypt(connection.encryptedToken());
    }
}
