package com.ivan.nexus.application.database;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.database.CellPatchGrouper;
import com.ivan.nexus.domain.database.ControlPlaneDatabase;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.interfaces.database.DatabaseDtos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EditDatabaseCells {
    private final DiscoverProjectDatabases discover;
    private final SqlExecutor jdbc;
    private final MongoExecutor mongo;
    private final RecordAudit recordAudit;
    private final UserDirectory users;

    public EditDatabaseCells(
            DiscoverProjectDatabases discover,
            SqlExecutor jdbc,
            MongoExecutor mongo,
            RecordAudit recordAudit,
            UserDirectory users) {
        this.discover = discover;
        this.jdbc = jdbc;
        this.mongo = mongo;
        this.recordAudit = recordAudit;
        this.users = users;
    }

    public QueryResult execute(
            String projectId,
            String databaseId,
            DatabaseDtos.CellsRequest body,
            String role,
            String username,
            String ip) {
        ControlPlaneDatabase.requireAdminForDataAccess(projectId, role);
        InstanceResolution resolution = GetDatabaseMetadata.requireReady(discover.resolve(projectId, databaseId));
        DatabaseEngine engine = resolution.instance().engine();
        boolean sqlBody = hasText(body.schema()) && hasText(body.table());
        boolean mongoBody = hasText(body.mongoDatabase()) && hasText(body.collection());
        List<DatabaseDtos.SqlInsertValues> insertValues =
                body.inserts() == null ? List.of() : body.inserts();
        List<Map<String, Object>> deleteMaps = body.deletes() == null ? List.of() : body.deletes();
        QueryResult result;
        if (engine == DatabaseEngine.MONGO) {
            if (!mongoBody || sqlBody) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed");
            }
            if (!insertValues.isEmpty() || !deleteMaps.isEmpty()) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed");
            }
            List<CellPatchGrouper.MongoPatch> mongoPatches = new ArrayList<>();
            for (DatabaseDtos.CellPatch patch : patches(body)) {
                if ("_id".equals(patch.field())) {
                    throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Cannot edit primary key");
                }
                mongoPatches.add(new CellPatchGrouper.MongoPatch(patch.id(), patch.field(), patch.value()));
            }
            result = mongo.updateDocuments(
                    resolution.target(),
                    body.mongoDatabase(),
                    body.collection(),
                    CellPatchGrouper.groupMongo(mongoPatches));
        } else {
            if (!sqlBody || mongoBody) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed");
            }
            List<CellPatchGrouper.SqlPatch> sqlPatches = new ArrayList<>();
            for (DatabaseDtos.CellPatch patch : patches(body)) {
                if (patch.primaryKey() != null && patch.primaryKey().containsKey(patch.column())) {
                    throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Cannot edit primary key");
                }
                sqlPatches.add(new CellPatchGrouper.SqlPatch(patch.primaryKey(), patch.column(), patch.value()));
            }
            List<CellPatchGrouper.SqlInsert> sqlInserts = new ArrayList<>();
            for (DatabaseDtos.SqlInsertValues insert : insertValues) {
                sqlInserts.add(new CellPatchGrouper.SqlInsert(
                        insert.values() == null ? Map.of() : insert.values()));
            }
            result = jdbc.applyCells(
                    engine,
                    resolution.target(),
                    body.schema(),
                    body.table(),
                    CellPatchGrouper.planSql(sqlPatches, sqlInserts, deleteMaps));
        }
        UUID userId = users.findIdByUsername(username).orElse(null);
        recordAudit.execute(
                userId,
                AuditAction.DB_CELL_EDIT,
                projectId,
                resolution.instance().service(),
                ip,
                Map.of(
                        "engine", engine.name(),
                        "service", resolution.instance().service(),
                        "databaseId", databaseId,
                        "class", "WRITE",
                        "rowCount", result.rowCount(),
                        "durationMs", result.durationMs()));
        return result;
    }

    private static List<DatabaseDtos.CellPatch> patches(DatabaseDtos.CellsRequest body) {
        return body.patches() == null ? List.of() : body.patches();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
