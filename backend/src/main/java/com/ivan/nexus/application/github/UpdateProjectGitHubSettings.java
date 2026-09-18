package com.ivan.nexus.application.github;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.deployment.ManagedProjectStore;
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
public class UpdateProjectGitHubSettings {
    private final ManagedProjectStore projects;
    private final UserDirectory users;
    private final RecordAudit recordAudit;
    private final RecordActivity recordActivity;

    public UpdateProjectGitHubSettings(
            ManagedProjectStore projects,
            UserDirectory users,
            RecordAudit recordAudit,
            RecordActivity recordActivity) {
        this.projects = projects;
        this.users = users;
        this.recordAudit = recordAudit;
        this.recordActivity = recordActivity;
    }

    @Transactional
    public ProjectGitHubLink execute(
            String projectId,
            boolean autodeployEnabled,
            String username,
            String ip) {
        ProjectGitHubLink existing = projects.findGitHubLink(projectId)
                .orElseThrow(() -> new DomainException(
                        NexusErrorCode.PROJECT_GITHUB_NOT_LINKED,
                        "Project is not linked to a GitHub repository"));
        projects.setAutodeployEnabled(projectId, autodeployEnabled);
        ProjectGitHubLink updated = new ProjectGitHubLink(
                existing.projectId(),
                existing.owner(),
                existing.repo(),
                existing.branch(),
                autodeployEnabled,
                existing.lastRemoteSha());

        UUID userId = users.findIdByUsername(username).orElse(null);
        Map<String, Object> metadata = Map.of(
                "owner", updated.owner(),
                "repo", updated.repo(),
                "branch", updated.branch(),
                "autodeployEnabled", autodeployEnabled);
        recordAudit.execute(
                userId,
                AuditAction.PROJECT_GITHUB_UPDATE,
                projectId,
                null,
                ip,
                metadata);
        recordActivity.execute(
                ActivityType.CONFIG_CHANGED,
                projectId,
                null,
                "GitHub autodeploy " + (autodeployEnabled ? "enabled" : "disabled"),
                metadata);
        return updated;
    }
}
