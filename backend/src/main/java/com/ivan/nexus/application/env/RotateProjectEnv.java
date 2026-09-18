package com.ivan.nexus.application.env;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class RotateProjectEnv {
    private final ProjectDotEnvStore dotEnvStore;
    private final RecordAudit recordAudit;
    private final RecordActivity recordActivity;
    private final UserDirectory users;

    public RotateProjectEnv(
            ProjectDotEnvStore dotEnvStore,
            RecordAudit recordAudit,
            RecordActivity recordActivity,
            UserDirectory users) {
        this.dotEnvStore = dotEnvStore;
        this.recordAudit = recordAudit;
        this.recordActivity = recordActivity;
        this.users = users;
    }

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

        Map<String, String> values = ListProjectEnv.readMutable(dotEnvStore, projectId);
        if (!values.containsKey(normalizedName)) {
            throw new DomainException(NexusErrorCode.ENV_VAR_NOT_FOUND, "Environment variable not found");
        }
        values.put(normalizedName, plaintextValue);
        dotEnvStore.write(projectId, values);

        Instant now = Instant.now();
        UUID userId = users.findIdByUsername(username).orElse(null);
        Map<String, Object> metadata = Map.of(
                "name", normalizedName,
                "operation", "rotate",
                "source", "dotenv");
        recordAudit.execute(userId, AuditAction.CONFIG_CHANGE, projectId, null, ip, metadata);
        recordActivity.execute(
                ActivityType.CONFIG_CHANGED,
                projectId,
                null,
                "Environment variable " + normalizedName + " rotated",
                metadata);

        return ListProjectEnv.toView(projectId, normalizedName, plaintextValue, now);
    }
}
