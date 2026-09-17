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

    public QueryResult execute(EditDatabaseCellsCommand command) {
        ControlPlaneDatabase.requireAdminForDataAccess(command.projectId(), command.role());
        InstanceResolution resolution =
                GetDatabaseMetadata.requireReady(discover.resolve(command.projectId(), command.databaseId()));
        DatabaseEngine engine = resolution.instance().engine();
        boolean sqlBody = hasText(command.schema()) && hasText(command.table());
        boolean mongoBody = hasText(command.mongoDatabase()) && hasText(command.collection());
        List<EditDatabaseCellsCommand.InsertValues> insertValues = command.inserts();
        List<Map<String, Object>> deleteMaps = command.deletes();
        QueryResult result;
        if (engine == DatabaseEngine.MONGO) {
            if (!mongoBody || sqlBody) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed");
            }
            if (!insertValues.isEmpty() || !deleteMaps.isEmpty()) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed");
            }
            List<CellPatchGrouper.MongoPatch> mongoPatches = new ArrayList<>();
            for (EditDatabaseCellsCommand.CellPatch patch : command.patches()) {
                if ("_id".equals(patch.field())) {
                    throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Cannot edit primary key");
                }
                mongoPatches.add(new CellPatchGrouper.MongoPatch(patch.id(), patch.field(), patch.value()));
            }
            result = mongo.updateDocuments(
                    resolution.target(),
                    command.mongoDatabase(),
                    command.collection(),
                    CellPatchGrouper.groupMongo(mongoPatches));
        } else {
            if (!sqlBody || mongoBody) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed");
            }
            List<CellPatchGrouper.SqlPatch> sqlPatches = new ArrayList<>();
            for (EditDatabaseCellsCommand.CellPatch patch : command.patches()) {
                if (patch.primaryKey() != null && patch.primaryKey().containsKey(patch.column())) {
                    throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Cannot edit primary key");
                }
                sqlPatches.add(new CellPatchGrouper.SqlPatch(patch.primaryKey(), patch.column(), patch.value()));
            }
            List<CellPatchGrouper.SqlInsert> sqlInserts = new ArrayList<>();
            for (EditDatabaseCellsCommand.InsertValues insert : insertValues) {
                sqlInserts.add(new CellPatchGrouper.SqlInsert(
                        insert.values() == null ? Map.of() : insert.values()));
            }
            result = jdbc.applyCells(
                    engine,
                    resolution.target(),
                    command.schema(),
                    command.table(),
                    CellPatchGrouper.planSql(sqlPatches, sqlInserts, deleteMaps));
        }
        UUID userId = users.findIdByUsername(command.username()).orElse(null);
        recordAudit.execute(
                userId,
                AuditAction.DB_CELL_EDIT,
                command.projectId(),
                resolution.instance().service(),
                command.ip(),
                Map.of(
                        "engine", engine.name(),
                        "service", resolution.instance().service(),
                        "databaseId", command.databaseId(),
                        "class", "WRITE",
                        "rowCount", result.rowCount(),
                        "durationMs", result.durationMs()));
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
