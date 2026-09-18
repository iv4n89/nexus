package com.ivan.nexus.application.env;

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
public class DeleteProjectEnv {
    private final ProjectEnvStore store;
    private final RecordAudit recordAudit;
    private final RecordActivity recordActivity;
    private final UserDirectory users;

    public DeleteProjectEnv(
            ProjectEnvStore store,
            RecordAudit recordAudit,
            RecordActivity recordActivity,
            UserDirectory users) {
        this.store = store;
        this.recordAudit = recordAudit;
        this.recordActivity = recordActivity;
        this.users = users;
    }

    @Transactional
    public void execute(String projectId, String name, String username, String ip) {
        String normalizedName = UpsertProjectEnv.requireName(name);
        if (store.findByProjectAndName(projectId, normalizedName).isEmpty()) {
            throw new DomainException(NexusErrorCode.ENV_VAR_NOT_FOUND, "Environment variable not found");
        }
        store.delete(projectId, normalizedName);

        UUID userId = users.findIdByUsername(username).orElse(null);
        Map<String, Object> metadata = Map.of("name", normalizedName, "operation", "delete");
        recordAudit.execute(userId, AuditAction.CONFIG_CHANGE, projectId, null, ip, metadata);
        recordActivity.execute(
                ActivityType.CONFIG_CHANGED,
                projectId,
                null,
                "Environment variable " + normalizedName + " deleted",
                metadata);
    }
}
