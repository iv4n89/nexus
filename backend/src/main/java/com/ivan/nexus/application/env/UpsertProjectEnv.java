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
import java.util.regex.Pattern;

@Service
public class UpsertProjectEnv {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final ProjectEnvStore store;
    private final SecretStore secretStore;
    private final RecordAudit recordAudit;
    private final RecordActivity recordActivity;
    private final UserDirectory users;

    public UpsertProjectEnv(
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
            boolean secret,
            String username,
            String ip) {
        String normalizedName = requireName(name);
        if (plaintextValue == null) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "value is required");
        }

        Instant now = Instant.now();
        StoredProjectEnvVar existing = store.findByProjectAndName(projectId, normalizedName).orElse(null);
        UUID id = existing == null ? UUID.randomUUID() : existing.id();
        Instant createdAt = existing == null ? now : existing.createdAt();
        String operation = existing == null ? "create" : "update";

        StoredProjectEnvVar saved = store.upsert(new StoredProjectEnvVar(
                id,
                projectId,
                normalizedName,
                secretStore.encrypt(plaintextValue),
                secret,
                createdAt,
                now));

        UUID userId = users.findIdByUsername(username).orElse(null);
        Map<String, Object> metadata = Map.of(
                "name", normalizedName,
                "operation", operation,
                "secret", secret);
        recordAudit.execute(userId, AuditAction.CONFIG_CHANGE, projectId, null, ip, metadata);
        recordActivity.execute(
                ActivityType.CONFIG_CHANGED,
                projectId,
                null,
                "Environment variable " + normalizedName + " " + (existing == null ? "created" : "updated"),
                metadata);

        return new ProjectEnvVarView(
                saved.id(),
                saved.projectId(),
                saved.name(),
                saved.secret(),
                secret ? null : plaintextValue,
                saved.createdAt(),
                saved.updatedAt());
    }

    static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "name is required");
        }
        String trimmed = name.trim();
        if (!NAME_PATTERN.matcher(trimmed).matches()) {
            throw new DomainException(
                    NexusErrorCode.OPERATION_NOT_ALLOWED,
                    "name must match [A-Za-z_][A-Za-z0-9_]*");
        }
        return trimmed;
    }
}
