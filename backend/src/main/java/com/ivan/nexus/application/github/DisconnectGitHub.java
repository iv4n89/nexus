package com.ivan.nexus.application.github;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class DisconnectGitHub {
    private final GitHubConnectionStore connectionStore;
    private final RecordAudit recordAudit;
    private final RecordActivity recordActivity;
    private final UserDirectory users;

    public DisconnectGitHub(
            GitHubConnectionStore connectionStore,
            RecordAudit recordAudit,
            RecordActivity recordActivity,
            UserDirectory users) {
        this.connectionStore = connectionStore;
        this.recordAudit = recordAudit;
        this.recordActivity = recordActivity;
        this.users = users;
    }

    @Transactional
    public void execute(String username, String ip) {
        StoredGitHubConnection existing = connectionStore.load()
                .orElseThrow(() -> new DomainException(
                        NexusErrorCode.GITHUB_NOT_CONNECTED, "GitHub is not connected"));
        connectionStore.clear();

        UUID userId = users.findIdByUsername(username).orElse(null);
        Map<String, Object> metadata = Map.of("githubLogin", existing.githubLogin());
        recordAudit.execute(userId, AuditAction.GITHUB_DISCONNECT, null, null, ip, metadata);
        recordActivity.execute(
                ActivityType.GITHUB_DISCONNECTED,
                null,
                null,
                "GitHub disconnected (" + existing.githubLogin() + ")",
                metadata);
    }
}
