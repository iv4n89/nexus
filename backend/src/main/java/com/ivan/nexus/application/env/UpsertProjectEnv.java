package com.ivan.nexus.application.env;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.env.DotEnvParser;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UpsertProjectEnv {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final ProjectDotEnvStore dotEnvStore;
    private final RecordAudit recordAudit;
    private final RecordActivity recordActivity;
    private final UserDirectory users;

    public UpsertProjectEnv(
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
            boolean secret,
            String username,
            String ip) {
        String normalizedName = requireName(name);
        if (plaintextValue == null) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "value is required");
        }

        Map<String, String> values = ListProjectEnv.readMutable(dotEnvStore, projectId);
        boolean created = !values.containsKey(normalizedName);
        values.put(normalizedName, plaintextValue);
        dotEnvStore.write(projectId, values);

        Instant now = Instant.now();
        boolean treatAsSecret = secret || DotEnvParser.looksSecret(normalizedName);
        UUID userId = users.findIdByUsername(username).orElse(null);
        Map<String, Object> metadata = Map.of(
                "name", normalizedName,
                "operation", created ? "create" : "update",
                "secret", treatAsSecret,
                "source", "dotenv");
        recordAudit.execute(userId, AuditAction.CONFIG_CHANGE, projectId, null, ip, metadata);
        recordActivity.execute(
                ActivityType.CONFIG_CHANGED,
                projectId,
                null,
                "Environment variable " + normalizedName + " " + (created ? "created" : "updated"),
                metadata);

        return ListProjectEnv.toView(projectId, normalizedName, plaintextValue, now);
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
