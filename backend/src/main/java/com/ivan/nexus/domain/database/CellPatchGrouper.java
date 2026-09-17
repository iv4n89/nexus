package com.ivan.nexus.domain.database;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CellPatchGrouper {
    public static final int MAX_ROWS = 100;

    private CellPatchGrouper() {}

    public record SqlPatch(Map<String, Object> primaryKey, String column, Object value) {}

    public record GroupedSqlUpdate(Map<String, Object> primaryKey, Map<String, Object> columns) {}

    public record MongoPatch(String id, String field, Object value) {}

    public record GroupedMongoUpdate(String id, Map<String, Object> fields) {}

    public static List<GroupedSqlUpdate> groupSql(List<SqlPatch> patches) {
        if (patches == null || patches.isEmpty()) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Patches are required");
        }
        LinkedHashMap<String, GroupedSqlUpdate> grouped = new LinkedHashMap<>();
        for (SqlPatch patch : patches) {
            if (patch.primaryKey() == null || patch.primaryKey().isEmpty()
                    || patch.column() == null || patch.column().isBlank()) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Primary key and column are required");
            }
            String key = canonicalPk(patch.primaryKey());
            GroupedSqlUpdate existing = grouped.get(key);
            LinkedHashMap<String, Object> columns = existing == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(existing.columns());
            columns.put(patch.column(), patch.value());
            LinkedHashMap<String, Object> pk = existing == null
                    ? new LinkedHashMap<>(patch.primaryKey())
                    : new LinkedHashMap<>(existing.primaryKey());
            grouped.put(key, new GroupedSqlUpdate(pk, columns));
        }
        if (grouped.size() > MAX_ROWS) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Too many rows");
        }
        List<GroupedSqlUpdate> result = new ArrayList<>();
        for (GroupedSqlUpdate row : grouped.values()) {
            result.add(new GroupedSqlUpdate(
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(row.primaryKey())),
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(row.columns()))));
        }
        return List.copyOf(result);
    }

    public static List<GroupedMongoUpdate> groupMongo(List<MongoPatch> patches) {
        if (patches == null || patches.isEmpty()) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Patches are required");
        }
        LinkedHashMap<String, GroupedMongoUpdate> grouped = new LinkedHashMap<>();
        for (MongoPatch patch : patches) {
            if (patch.id() == null || patch.id().isBlank() || patch.field() == null || patch.field().isBlank()) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Document id and field are required");
            }
            GroupedMongoUpdate existing = grouped.get(patch.id());
            LinkedHashMap<String, Object> fields = existing == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(existing.fields());
            fields.put(patch.field(), patch.value());
            grouped.put(patch.id(), new GroupedMongoUpdate(patch.id(), fields));
        }
        if (grouped.size() > MAX_ROWS) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Too many rows");
        }
        List<GroupedMongoUpdate> result = new ArrayList<>();
        for (GroupedMongoUpdate row : grouped.values()) {
            result.add(new GroupedMongoUpdate(
                    row.id(),
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(row.fields()))));
        }
        return List.copyOf(result);
    }

    private static String canonicalPk(Map<String, Object> primaryKey) {
        return primaryKey.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + Objects.toString(entry.getValue()))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }
}
