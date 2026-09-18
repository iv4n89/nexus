package com.ivan.nexus.application.github;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.secrets.SecretStore;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ConnectGitHub {
    private final GitHubClient gitHubClient;
    private final GitHubOAuthStateStore stateStore;
    private final GitHubConnectionStore connectionStore;
    private final SecretStore secretStore;
    private final RecordAudit recordAudit;
    private final RecordActivity recordActivity;
    private final UserDirectory users;

    public ConnectGitHub(
            GitHubClient gitHubClient,
            GitHubOAuthStateStore stateStore,
            GitHubConnectionStore connectionStore,
            SecretStore secretStore,
            RecordAudit recordAudit,
            RecordActivity recordActivity,
            UserDirectory users) {
        this.gitHubClient = gitHubClient;
        this.stateStore = stateStore;
        this.connectionStore = connectionStore;
        this.secretStore = secretStore;
        this.recordAudit = recordAudit;
        this.recordActivity = recordActivity;
        this.users = users;
    }

    @Transactional
    public GitHubConnectionView execute(String code, String state, String username, String ip) {
        if (code == null || code.isBlank()) {
            throw new DomainException(NexusErrorCode.GITHUB_OAUTH_FAILED, "Missing OAuth code");
        }
        if (!stateStore.consume(state)) {
            throw new DomainException(NexusErrorCode.GITHUB_OAUTH_STATE_INVALID, "Invalid or expired OAuth state");
        }

        GitHubOAuthToken token;
        GitHubIdentity identity;
        try {
            token = gitHubClient.exchangeCode(code);
            identity = gitHubClient.getAuthenticatedUser(token.accessToken());
        } catch (DomainException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new DomainException(NexusErrorCode.GITHUB_OAUTH_FAILED, "GitHub OAuth exchange failed");
        }

        Instant now = Instant.now();
        UUID id = connectionStore.load().map(StoredGitHubConnection::id).orElseGet(UUID::randomUUID);
        String encryptedRefresh = token.refreshToken() == null || token.refreshToken().isBlank()
                ? null
                : secretStore.encrypt(token.refreshToken());
        StoredGitHubConnection connection = new StoredGitHubConnection(
                id,
                identity.login(),
                secretStore.encrypt(token.accessToken()),
                encryptedRefresh,
                token.scope(),
                now,
                now);
        connectionStore.save(connection);

        UUID userId = users.findIdByUsername(username).orElse(null);
        Map<String, Object> metadata = Map.of("githubLogin", identity.login());
        recordAudit.execute(userId, AuditAction.GITHUB_CONNECT, null, null, ip, metadata);
        recordActivity.execute(
                ActivityType.GITHUB_CONNECTED,
                null,
                null,
                "GitHub connected as " + identity.login(),
                metadata);

        return GitHubConnectionView.from(connection);
    }
}
