package com.ivan.nexus.application.database;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.database.ControlPlaneDatabase;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.database.JdbcQueryExecutor;
import com.ivan.nexus.infrastructure.database.MongoQueryExecutor;
import com.ivan.nexus.infrastructure.persistence.user.UserEntity;
import com.ivan.nexus.infrastructure.persistence.user.UserJpaRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class EditDatabaseCell {
    private final DiscoverProjectDatabases discover;
    private final JdbcQueryExecutor jdbc;
    private final MongoQueryExecutor mongo;
    private final RecordAudit recordAudit;
    private final UserJpaRepository users;

    public EditDatabaseCell(
            DiscoverProjectDatabases discover,
            JdbcQueryExecutor jdbc,
            MongoQueryExecutor mongo,
            RecordAudit recordAudit,
            UserJpaRepository users) {
        this.discover = discover;
        this.jdbc = jdbc;
        this.mongo = mongo;
        this.recordAudit = recordAudit;
        this.users = users;
    }

    public QueryResult execute(
            String projectId,
            String databaseId,
            String schema,
            String table,
            Map<String, Object> primaryKey,
            String column,
            Object value,
            String mongoDatabase,
            String collection,
            String id,
            String field,
            String role,
            String username,
            String ip) {
        ControlPlaneDatabase.requireAdminForDataAccess(projectId, role);
        InstanceResolution resolution = GetDatabaseMetadata.requireReady(discover.resolve(projectId, databaseId));
        QueryResult result;
        if (resolution.instance().engine() == DatabaseEngine.MONGO) {
            if (id == null || id.isBlank() || field == null || field.isBlank()) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Document id is required");
            }
            result = mongo.updateCell(resolution.target(), mongoDatabase, collection, id, field, value);
        } else {
            result = jdbc.updateCell(
                    resolution.instance().engine(),
                    resolution.target(),
                    schema,
                    table,
                    primaryKey,
                    column,
                    value);
        }
        UUID userId = users.findByUsername(username).map(UserEntity::getId).orElse(null);
        recordAudit.execute(
                userId,
                AuditAction.DB_CELL_EDIT,
                projectId,
                resolution.instance().service(),
                ip,
                Map.of(
                        "engine", resolution.instance().engine().name(),
                        "service", resolution.instance().service(),
                        "databaseId", databaseId,
                        "class", "WRITE",
                        "rowCount", result.rowCount(),
                        "durationMs", result.durationMs()));
        return result;
    }
}
