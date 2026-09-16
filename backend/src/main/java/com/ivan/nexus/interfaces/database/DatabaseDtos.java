package com.ivan.nexus.interfaces.database;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.infrastructure.database.JdbcQueryExecutor;
import com.ivan.nexus.infrastructure.database.MongoQueryExecutor;

import java.util.List;
import java.util.Map;

public final class DatabaseDtos {
    private DatabaseDtos() {}

    public record InstanceResponse(
            String id,
            String service,
            DatabaseEngine engine,
            DatabaseStatus status,
            String defaultDatabase) {}

    public record QueryRequest(String statement, boolean confirmDestructive) {}

    public record QueryResponse(
            List<String> columns,
            List<List<Object>> rows,
            boolean truncated,
            long durationMs,
            int rowCount) {}

    public record CellRequest(
            String schema,
            String table,
            Map<String, Object> primaryKey,
            String column,
            Object value,
            String mongoDatabase,
            String collection,
            String id,
            String field) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MetadataResponse(
            DatabaseEngine engine,
            List<SchemaResponse> schemas,
            List<MongoDatabaseResponse> databases) {}

    public record SchemaResponse(String name, List<TableResponse> tables) {}

    public record TableResponse(String name, String type, List<String> primaryKey) {}

    public record MongoDatabaseResponse(String name, List<String> collections) {}

    public static MetadataResponse fromSql(DatabaseEngine engine, JdbcQueryExecutor.SqlCatalog catalog) {
        Map<String, List<TableResponse>> bySchema = new java.util.LinkedHashMap<>();
        for (JdbcQueryExecutor.SqlTable table : catalog.tables()) {
            bySchema.computeIfAbsent(table.schema(), key -> new java.util.ArrayList<>())
                    .add(new TableResponse(table.name(), table.type(), table.primaryKey()));
        }
        List<SchemaResponse> schemas = bySchema.entrySet().stream()
                .map(entry -> new SchemaResponse(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        return new MetadataResponse(engine, schemas, null);
    }

    public static MetadataResponse fromMongo(MongoQueryExecutor.MongoCatalog catalog) {
        List<MongoDatabaseResponse> databases = catalog.databases().stream()
                .map(db -> new MongoDatabaseResponse(db.name(), db.collections()))
                .toList();
        return new MetadataResponse(DatabaseEngine.MONGO, null, databases);
    }
}
