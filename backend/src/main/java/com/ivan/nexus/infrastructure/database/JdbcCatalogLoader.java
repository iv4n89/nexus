package com.ivan.nexus.infrastructure.database;

import com.ivan.nexus.application.database.SqlExecutor.SqlCatalog;
import com.ivan.nexus.application.database.SqlExecutor.SqlColumn;
import com.ivan.nexus.application.database.SqlExecutor.SqlTable;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.database.StatementClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JdbcCatalogLoader {
    private final JdbcQueryExecutor executor;

    JdbcCatalogLoader(JdbcQueryExecutor executor) {
        this.executor = executor;
    }

    SqlCatalog metadata(DatabaseEngine engine, ResolvedTarget target) {
        String excluded = engine == DatabaseEngine.MYSQL
                ? "'mysql','sys','performance_schema','information_schema'"
                : "'pg_catalog','information_schema'";
        String tablesSql = """
                SELECT table_schema, table_name, table_type
                FROM information_schema.tables
                WHERE table_schema NOT IN (%s)
                ORDER BY 1, 2
                """.formatted(excluded);
        QueryResult tables = executor.query(engine, target, tablesSql, StatementClass.READ, 500);
        Map<String, Set<String>> primaryKeys = loadPrimaryKeys(engine, target, excluded);
        Map<String, List<SqlColumn>> columns = loadColumns(engine, target, excluded);
        List<SqlTable> mapped = new ArrayList<>();
        for (List<Object> row : tables.rows()) {
            String schema = String.valueOf(row.get(0));
            String name = String.valueOf(row.get(1));
            String typeRaw = String.valueOf(row.get(2));
            String type = typeRaw != null && typeRaw.toUpperCase().contains("VIEW") ? "view" : "table";
            String key = schema + "." + name;
            mapped.add(new SqlTable(
                    schema,
                    name,
                    type,
                    List.copyOf(primaryKeys.getOrDefault(key, Set.of())),
                    columns.getOrDefault(key, List.of())));
        }
        return new SqlCatalog(mapped);
    }

    Map<String, Set<String>> loadPrimaryKeys(DatabaseEngine engine, ResolvedTarget target, String excluded) {
        String sql = """
                SELECT kcu.table_schema, kcu.table_name, kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                 AND tc.table_name = kcu.table_name
                WHERE tc.constraint_type = 'PRIMARY KEY'
                  AND kcu.table_schema NOT IN (%s)
                ORDER BY kcu.ordinal_position
                """.formatted(excluded);
        QueryResult result = executor.query(engine, target, sql, StatementClass.READ, 500);
        Map<String, Set<String>> keys = new LinkedHashMap<>();
        for (List<Object> row : result.rows()) {
            String key = row.get(0) + "." + row.get(1);
            keys.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(String.valueOf(row.get(2)));
        }
        return keys;
    }

    Map<String, List<SqlColumn>> loadColumns(DatabaseEngine engine, ResolvedTarget target, String excluded) {
        String sql = """
                SELECT table_schema, table_name, column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema NOT IN (%s)
                ORDER BY ordinal_position
                """.formatted(excluded);
        QueryResult result = executor.query(engine, target, sql, StatementClass.READ, 2000);
        Map<String, List<SqlColumn>> columns = new LinkedHashMap<>();
        for (List<Object> row : result.rows()) {
            String key = row.get(0) + "." + row.get(1);
            columns.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new SqlColumn(
                    String.valueOf(row.get(2)),
                    String.valueOf(row.get(3)),
                    "YES".equalsIgnoreCase(String.valueOf(row.get(4)))));
        }
        return columns;
    }
}
