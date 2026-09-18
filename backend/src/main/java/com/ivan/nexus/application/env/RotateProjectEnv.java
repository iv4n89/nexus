package com.ivan.nexus.application.env;

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
public class RotateProjectEnv {
    private final ProjectEnvStore store;
    private final SecretStore secretStore;
    private final RecordAudit recordAudit;
    private final RecordActivity recordActivity;
    private final UserDirectory users;

    public RotateProjectEnv(
            ProjectEnvStore store,
            SecretStore secretStore,
            RecordAudit recordAudit,
            RecordActivity recordActivity,
            UserDirectory users) {
        this.store = store;
        this.secretStore = secretStore;
        this.recordAudit = recordAudit;
        this.recordActivity = recordActivity;
        this.users = users;
    }

    @Transactional
    public ProjectEnvVarView execute(
            String projectId,
            String name,
            String plaintextValue,
            String username,
            String ip) {
        String normalizedName = UpsertProjectEnv.requireName(name);
        if (plaintextValue == null) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "value is required");
        }

        StoredProjectEnvVar existing = store.findByProjectAndName(projectId, normalizedName)
                .orElseThrow(() -> new DomainException(
                        NexusErrorCode.ENV_VAR_NOT_FOUND, "Environment variable not found"));

        Instant now = Instant.now();
        StoredProjectEnvVar saved = store.upsert(new StoredProjectEnvVar(
                existing.id(),
                existing.projectId(),
                existing.name(),
                secretStore.encrypt(plaintextValue),
                existing.secret(),
                existing.createdAt(),
                now));

        UUID userId = users.findIdByUsername(username).orElse(null);
        Map<String, Object> metadata = Map.of(
                "name", normalizedName,
                "operation", "rotate",
                "secret", existing.secret());
        recordAudit.execute(userId, AuditAction.CONFIG_CHANGE, projectId, null, ip, metadata);
        recordActivity.execute(
                ActivityType.CONFIG_CHANGED,
                projectId,
                null,
                "Environment variable " + normalizedName + " rotated",
                metadata);

        return new ProjectEnvVarView(
                saved.id(),
                saved.projectId(),
                saved.name(),
                saved.secret(),
                existing.secret() ? null : plaintextValue,
                saved.createdAt(),
                saved.updatedAt());
    }
}
