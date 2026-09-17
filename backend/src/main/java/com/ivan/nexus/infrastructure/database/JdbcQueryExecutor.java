package com.ivan.nexus.infrastructure.database;

import com.ivan.nexus.application.database.SqlExecutor;
import com.ivan.nexus.application.database.SqlExecutor.SqlCatalog;
import com.ivan.nexus.application.database.SqlExecutor.SqlColumn;
import com.ivan.nexus.application.database.SqlExecutor.SqlTable;
import com.ivan.nexus.domain.database.CellPatchGrouper;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.database.SqlIdentifierQuoter;
import com.ivan.nexus.domain.database.StatementClass;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Component
public class JdbcQueryExecutor implements SqlExecutor {
    private static final Logger log = LoggerFactory.getLogger(JdbcQueryExecutor.class);
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int SOCKET_TIMEOUT_SECONDS = 15;
    private static final int QUERY_TIMEOUT_SECONDS = 10;
    static final int MAX_CELL_CHARS = 8192;

    @Override
    public QueryResult query(
            DatabaseEngine engine,
            ResolvedTarget target,
            String sql,
            StatementClass statementClass,
            int limit) {
        Properties props = connectionProperties(engine, target);
        String url = jdbcUrl(engine, target);
        long start = System.nanoTime();
        try (Connection conn = DriverManager.getConnection(url, props);
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            stmt.setMaxFieldSize(MAX_CELL_CHARS);
            if (returnsResultSet(sql, statementClass)) {
                stmt.setMaxRows(limit + 1);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    return readResult(rs, limit, elapsedMs(start));
                }
            }
            int updated = stmt.executeUpdate(sql);
            return new QueryResult(
                    List.of("updateCount"),
                    List.of(List.of(updated)),
                    false,
                    elapsedMs(start),
                    1);
        } catch (SQLTimeoutException ex) {
            throw new DomainException(NexusErrorCode.QUERY_TIMEOUT, "Query timed out");
        } catch (SQLException ex) {
            if (isTimeout(ex)) {
                throw new DomainException(NexusErrorCode.QUERY_TIMEOUT, "Query timed out");
            }
            throw new DomainException(
                    NexusErrorCode.QUERY_FAILED,
                    SecretSanitizer.strip(target.password(), ex.getMessage()));
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Database query failed", ex);
            throw new DomainException(
                    NexusErrorCode.QUERY_FAILED,
                    SecretSanitizer.strip(
                            target.password(),
                            ex.getMessage() == null ? "Query failed" : ex.getMessage()));
        }
    }

    @Override
    public QueryResult preview(DatabaseEngine engine, ResolvedTarget target, String schema, String table) {
        String sql = "SELECT * FROM "
                + SqlIdentifierQuoter.quote(engine, schema)
                + "."
                + SqlIdentifierQuoter.quote(engine, table)
                + " LIMIT 100";
        return query(engine, target, sql, StatementClass.READ, 100);
    }

    public QueryResult updateCell(
            DatabaseEngine engine,
            ResolvedTarget target,
            String schema,
            String table,
            Map<String, Object> primaryKey,
            String column,
            Object value) {
        return updateCells(
                engine,
                target,
                schema,
                table,
                CellPatchGrouper.groupSql(List.of(new CellPatchGrouper.SqlPatch(primaryKey, column, value))));
    }

    public QueryResult updateCells(
            DatabaseEngine engine,
            ResolvedTarget target,
            String schema,
            String table,
            List<CellPatchGrouper.GroupedSqlUpdate> grouped) {
        if (grouped == null || grouped.isEmpty()) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Patches are required");
        }
        return applyCells(
                engine,
                target,
                schema,
                table,
                new CellPatchGrouper.SqlWriteBatch(List.of(), grouped, List.of()));
    }

    @Override
    public QueryResult applyCells(
            DatabaseEngine engine,
            ResolvedTarget target,
            String schema,
            String table,
            CellPatchGrouper.SqlWriteBatch batch) {
        if (batch == null
                || (batch.deletes().isEmpty() && batch.updates().isEmpty() && batch.inserts().isEmpty())) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Patches are required");
        }
        Properties props = connectionProperties(engine, target);
        String url = jdbcUrl(engine, target);
        long start = System.nanoTime();
        int written = batch.deletes().size() + batch.updates().size() + batch.inserts().size();
        try (Connection conn = DriverManager.getConnection(url, props)) {
            conn.setAutoCommit(false);
            try {
                for (CellPatchGrouper.SqlDelete row : batch.deletes()) {
                    String sql = buildDeleteSql(engine, schema, table, row);
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                        int index = 1;
                        for (Object value : row.primaryKey().values()) {
                            stmt.setObject(index++, value);
                        }
                        expectOneRow(conn, stmt);
                    }
                }
                for (CellPatchGrouper.GroupedSqlUpdate row : batch.updates()) {
                    String sql = buildUpdateSql(engine, schema, table, row);
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                        int index = 1;
                        for (Object value : row.columns().values()) {
                            stmt.setObject(index++, value);
                        }
                        for (Object value : row.primaryKey().values()) {
                            stmt.setObject(index++, value);
                        }
                        expectOneRow(conn, stmt);
                    }
                }
                for (CellPatchGrouper.SqlInsert row : batch.inserts()) {
                    String sql = buildInsertSql(engine, schema, table, row);
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                        int index = 1;
                        for (Object value : row.values().values()) {
                            stmt.setObject(index++, value);
                        }
                        expectOneRow(conn, stmt);
                    }
                }
                conn.commit();
                return new QueryResult(
                        List.of("updateCount"),
                        List.of(List.of(written)),
                        false,
                        elapsedMs(start),
                        written);
            } catch (DomainException ex) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                    // already rolled back for unmatched primary key
                }
                throw ex;
            } catch (SQLTimeoutException ex) {
                conn.rollback();
                throw new DomainException(NexusErrorCode.QUERY_TIMEOUT, "Query timed out");
            } catch (SQLException ex) {
                conn.rollback();
                throw new DomainException(
                        NexusErrorCode.QUERY_FAILED,
                        SecretSanitizer.strip(target.password(), ex.getMessage()));
            }
        } catch (SQLException ex) {
            throw new DomainException(
                    NexusErrorCode.QUERY_FAILED,
                    SecretSanitizer.strip(target.password(), ex.getMessage()));
        }
    }

    private static void expectOneRow(Connection conn, PreparedStatement stmt) throws SQLException {
        int updated = stmt.executeUpdate();
        if (updated != 1) {
            conn.rollback();
            throw new DomainException(NexusErrorCode.QUERY_FAILED, "No row matched primary key");
        }
    }

    static String buildUpdateSql(
            DatabaseEngine engine,
            String schema,
            String table,
            CellPatchGrouper.GroupedSqlUpdate row) {
        StringBuilder sql = new StringBuilder("UPDATE ")
                .append(SqlIdentifierQuoter.quote(engine, schema))
                .append('.')
                .append(SqlIdentifierQuoter.quote(engine, table))
                .append(" SET ");
        int i = 0;
        for (String column : row.columns().keySet()) {
            if (i++ > 0) {
                sql.append(", ");
            }
            sql.append(SqlIdentifierQuoter.quote(engine, column)).append(" = ?");
        }
        sql.append(" WHERE ");
        int j = 0;
        for (String column : row.primaryKey().keySet()) {
            if (j++ > 0) {
                sql.append(" AND ");
            }
            sql.append(SqlIdentifierQuoter.quote(engine, column)).append(" = ?");
        }
        return sql.toString();
    }

    static String buildDeleteSql(
            DatabaseEngine engine, String schema, String table, CellPatchGrouper.SqlDelete row) {
        StringBuilder sql = new StringBuilder("DELETE FROM ")
                .append(SqlIdentifierQuoter.quote(engine, schema))
                .append('.')
                .append(SqlIdentifierQuoter.quote(engine, table))
                .append(" WHERE ");
        int i = 0;
        for (String column : row.primaryKey().keySet()) {
            if (i++ > 0) {
                sql.append(" AND ");
            }
            sql.append(SqlIdentifierQuoter.quote(engine, column)).append(" = ?");
        }
        return sql.toString();
    }

    static String buildInsertSql(
            DatabaseEngine engine, String schema, String table, CellPatchGrouper.SqlInsert row) {
        String tableRef = SqlIdentifierQuoter.quote(engine, schema)
                + "."
                + SqlIdentifierQuoter.quote(engine, table);
        if (row.values() == null || row.values().isEmpty()) {
            if (engine == DatabaseEngine.MYSQL) {
                return "INSERT INTO " + tableRef + " () VALUES ()";
            }
            return "INSERT INTO " + tableRef + " DEFAULT VALUES";
        }
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        int i = 0;
        for (String column : row.values().keySet()) {
            if (i++ > 0) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(SqlIdentifierQuoter.quote(engine, column));
            placeholders.append("?");
        }
        return "INSERT INTO " + tableRef + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    @Override
    public SqlCatalog metadata(DatabaseEngine engine, ResolvedTarget target) {
        String excluded = engine == DatabaseEngine.MYSQL
                ? "'mysql','sys','performance_schema','information_schema'"
                : "'pg_catalog','information_schema'";
        String tablesSql = """
                SELECT table_schema, table_name, table_type
                FROM information_schema.tables
                WHERE table_schema NOT IN (%s)
                ORDER BY 1, 2
                """.formatted(excluded);
        QueryResult tables = query(engine, target, tablesSql, StatementClass.READ, 500);
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

    private Map<String, Set<String>> loadPrimaryKeys(DatabaseEngine engine, ResolvedTarget target, String excluded) {
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
        QueryResult result = query(engine, target, sql, StatementClass.READ, 500);
        Map<String, Set<String>> keys = new LinkedHashMap<>();
        for (List<Object> row : result.rows()) {
            String key = row.get(0) + "." + row.get(1);
            keys.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(String.valueOf(row.get(2)));
        }
        return keys;
    }

    private Map<String, List<SqlColumn>> loadColumns(DatabaseEngine engine, ResolvedTarget target, String excluded) {
        String sql = """
                SELECT table_schema, table_name, column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema NOT IN (%s)
                ORDER BY ordinal_position
                """.formatted(excluded);
        QueryResult result = query(engine, target, sql, StatementClass.READ, 2000);
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

    private static QueryResult readResult(ResultSet rs, int limit, long durationMs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }
        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= limit) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(readCell(rs, i));
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows, truncated, durationMs, rows.size());
    }

    static Object readCell(ResultSet rs, int index) throws SQLException {
        try {
            return cell(rs.getObject(index));
        } catch (SQLException ex) {
            try {
                return cell(rs.getString(index));
            } catch (SQLException ignored) {
                return "<unreadable>";
            }
        }
    }

    static Object cell(Object value) {
        if (value == null || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number number) {
            if (number instanceof Double d && (d.isNaN() || d.isInfinite())) {
                return d.toString();
            }
            if (number instanceof Float f && (f.isNaN() || f.isInfinite())) {
                return f.toString();
            }
            return value;
        }
        if (value instanceof byte[] bytes) {
            return "<binary " + bytes.length + " bytes>";
        }
        String text = value instanceof String s ? s : String.valueOf(value);
        if (text.length() < MAX_CELL_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CELL_CHARS - 1) + "…";
    }

    static boolean returnsResultSet(String sql, StatementClass statementClass) {
        if (statementClass == StatementClass.READ) {
            return true;
        }
        String head = sql == null ? "" : sql.stripLeading();
        return startsWithIgnoreCase(head, "SELECT") || startsWithIgnoreCase(head, "WITH");
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    static Properties connectionProperties(DatabaseEngine engine, ResolvedTarget target) {
        Properties props = new Properties();
        if (target.username() != null) {
            props.setProperty("user", safeName(target.username()));
        }
        if (target.password() != null) {
            props.setProperty("password", target.password());
        }
        if (engine == DatabaseEngine.MYSQL) {
            props.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS * 1000));
            props.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_SECONDS * 1000));
        } else {
            props.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
            props.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_SECONDS));
        }
        if (engine == DatabaseEngine.POSTGRES) {
            props.setProperty("options", "-c statement_timeout=10000");
        }
        return props;
    }

    static String jdbcUrl(DatabaseEngine engine, ResolvedTarget target) {
        String database = safeName(target.defaultDatabase());
        if (engine == DatabaseEngine.POSTGRES) {
            return "jdbc:postgresql://%s:%d/%s".formatted(target.host(), target.port(), database);
        }
        return "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true".formatted(
                target.host(), target.port(), database);
    }

    static String safeName(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.]+") || value.contains("..")
                || value.startsWith(".") || value.endsWith(".")) {
            throw new DomainException(NexusErrorCode.QUERY_FAILED, "Invalid database name");
        }
        return value;
    }

    private static boolean isTimeout(SQLException ex) {
        String state = ex.getSQLState();
        return "57014".equals(state) || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timeout"));
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
