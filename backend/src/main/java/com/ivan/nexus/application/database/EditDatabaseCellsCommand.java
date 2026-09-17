package com.ivan.nexus.application.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record EditDatabaseCellsCommand(
        String projectId,
        String databaseId,
        String role,
        String username,
        String ip,
        String schema,
        String table,
        String mongoDatabase,
        String collection,
        List<CellPatch> patches,
        List<InsertValues> inserts,
        List<Map<String, Object>> deletes) {

    public EditDatabaseCellsCommand {
        patches = immutableList(patches);
        inserts = immutableList(inserts);
        deletes = immutableMaps(deletes);
    }

    public record CellPatch(
            Map<String, Object> primaryKey,
            String column,
            Object value,
            String id,
            String field) {

        public CellPatch {
            primaryKey = immutableMap(primaryKey);
        }
    }

    public record InsertValues(Map<String, Object> values) {
        public InsertValues {
            values = immutableMap(values);
        }
    }

    private static <T> List<T> immutableList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static List<Map<String, Object>> immutableMaps(List<Map<String, Object>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> copies = new ArrayList<>(values.size());
        for (Map<String, Object> value : values) {
            copies.add(immutableMap(value));
        }
        return Collections.unmodifiableList(copies);
    }

    private static Map<String, Object> immutableMap(Map<String, Object> values) {
        if (values == null) {
            return null;
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
