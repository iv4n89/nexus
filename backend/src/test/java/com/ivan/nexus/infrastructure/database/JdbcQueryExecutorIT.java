package com.ivan.nexus.infrastructure.database;

import com.ivan.nexus.domain.database.CellPatchGrouper;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.database.StatementClass;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class JdbcQueryExecutorIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lab")
            .withUsername("lab")
            .withPassword("lab");

    private final JdbcQueryExecutor executor = new JdbcQueryExecutor();

    @BeforeAll
    static void schema() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t (n INT PRIMARY KEY)");
            stmt.execute("INSERT INTO t (n) VALUES (1), (2), (3)");
        }
    }

    @Test
    void selectAndCap() {
        ResolvedTarget target = new ResolvedTarget(
                postgres.getHost(),
                postgres.getMappedPort(5432),
                postgres.getUsername(),
                postgres.getPassword(),
                postgres.getDatabaseName());
        QueryResult result = executor.query(
                DatabaseEngine.POSTGRES,
                target,
                "SELECT n FROM t ORDER BY n",
                StatementClass.READ,
                500);
        assertEquals(List.of("n"), result.columns());
        assertEquals(3, result.rowCount());
        assertFalse(result.truncated());
        assertEquals(1, ((Number) result.rows().getFirst().getFirst()).intValue());

        QueryResult capped = executor.query(
                DatabaseEngine.POSTGRES,
                target,
                "SELECT n FROM t ORDER BY n",
                StatementClass.READ,
                2);
        assertTrue(capped.truncated());
        assertEquals(2, capped.rowCount());
    }

    @Test
    void previewSelectsQuotedSchemaAndTable() {
        ResolvedTarget target = target();
        QueryResult result = executor.preview(DatabaseEngine.POSTGRES, target, "public", "t");
        assertEquals(3, result.rowCount());
        assertEquals(List.of("n"), result.columns());
    }

    @Test
    void previewAllowsSqlNullCells() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE nullable_t (id INT PRIMARY KEY, note TEXT)");
            stmt.execute("INSERT INTO nullable_t (id, note) VALUES (1, NULL)");
        }
        QueryResult result = executor.preview(DatabaseEngine.POSTGRES, target(), "public", "nullable_t");
        assertEquals(1, result.rowCount());
        assertNull(result.rows().getFirst().get(1));
    }

    @Test
    void missingPreviewTableIsQueryFailedNotUnreachable() {
        DomainException ex = assertThrows(
                DomainException.class,
                () -> executor.preview(DatabaseEngine.POSTGRES, target(), "public", "missing"));
        assertEquals(NexusErrorCode.QUERY_FAILED, ex.getCode());
    }

    @Test
    void truncatesOversizedTextCells() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE bulky (id INT PRIMARY KEY, body TEXT)");
            stmt.execute("INSERT INTO bulky (id, body) VALUES (1, repeat('x', 20000))");
        }
        QueryResult result = executor.preview(DatabaseEngine.POSTGRES, target(), "public", "bulky");
        assertEquals(1, result.rowCount());
        String body = (String) result.rows().getFirst().get(1);
        assertTrue(body.length() <= 8192);
        assertTrue(body.endsWith("…"));
    }

    @Test
    void selectForUpdateStillReturnsRows() {
        QueryResult locked = executor.query(
                DatabaseEngine.POSTGRES,
                target(),
                "SELECT n FROM t WHERE n = 1 FOR UPDATE",
                StatementClass.WRITE,
                500);
        assertEquals(1, locked.rowCount());
        assertEquals(1, ((Number) locked.rows().getFirst().getFirst()).intValue());
    }

    @Test
    void previewReadsReservedNamesAndMixedColumnTypes() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE "user" (
                        id INT PRIMARY KEY,
                        loc POINT,
                        payload JSONB,
                        blob BYTEA,
                        seen TIMESTAMPTZ
                    )
                    """);
            stmt.execute("""
                    INSERT INTO "user" (id, loc, payload, blob, seen)
                    VALUES (1, POINT(1,2), '{"ok":true}', decode('DEAD', 'hex'), TIMESTAMPTZ '2026-09-17 08:00:00+00')
                    """);
        }
        QueryResult result = executor.preview(DatabaseEngine.POSTGRES, target(), "public", "user");
        assertEquals(1, result.rowCount());
        assertEquals(5, result.columns().size());
        assertEquals(1, ((Number) result.rows().getFirst().getFirst()).intValue());
    }

    @Test
    void injectedDatabaseNameDoesNotReachDriver() {
        ResolvedTarget poisoned = new ResolvedTarget(
                postgres.getHost(),
                postgres.getMappedPort(5432),
                postgres.getUsername(),
                postgres.getPassword(),
                "lab?allowMultiQueries=true");
        DomainException ex = assertThrows(
                DomainException.class,
                () -> executor.query(
                        DatabaseEngine.POSTGRES,
                        poisoned,
                        "SELECT 1",
                        StatementClass.READ,
                        10));
        assertEquals(NexusErrorCode.QUERY_FAILED, ex.getCode());
        assertTrue(ex.getMessage().toLowerCase().contains("invalid"));
    }

    @Test
    void updateCellsRollsBackWhenSecondRowFails() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE jobs (
                        id INT PRIMARY KEY,
                        status TEXT NOT NULL CHECK (status IN ('queued', 'running')),
                        name TEXT
                    )
                    """);
            stmt.execute("INSERT INTO jobs (id, status, name) VALUES (1, 'queued', 'ingest'), (2, 'queued', 'export')");
        }
        List<CellPatchGrouper.SqlPatch> patches = List.of(
                new CellPatchGrouper.SqlPatch(Map.of("id", 1), "status", "running"),
                new CellPatchGrouper.SqlPatch(Map.of("id", 2), "status", "bogus"));
        DomainException ex = assertThrows(
                DomainException.class,
                () -> executor.updateCells(
                        DatabaseEngine.POSTGRES,
                        target(),
                        "public",
                        "jobs",
                        CellPatchGrouper.groupSql(patches)));
        assertEquals(NexusErrorCode.QUERY_FAILED, ex.getCode());
        QueryResult still = executor.query(
                DatabaseEngine.POSTGRES,
                target(),
                "SELECT status FROM jobs ORDER BY id",
                StatementClass.READ,
                10);
        assertEquals("queued", still.rows().get(0).get(0));
        assertEquals("queued", still.rows().get(1).get(0));
    }

    @Test
    void updateCellsFailsWhenPrimaryKeyMatchesNoRow() {
        List<CellPatchGrouper.GroupedSqlUpdate> grouped = CellPatchGrouper.groupSql(List.of(
                new CellPatchGrouper.SqlPatch(Map.of("n", 99), "n", 99)));
        DomainException ex = assertThrows(
                DomainException.class,
                () -> executor.updateCells(DatabaseEngine.POSTGRES, target(), "public", "t", grouped));
        assertEquals(NexusErrorCode.QUERY_FAILED, ex.getCode());
        assertTrue(ex.getMessage().toLowerCase().contains("primary key"));
    }

    private static ResolvedTarget target() {
        return new ResolvedTarget(
                postgres.getHost(),
                postgres.getMappedPort(5432),
                postgres.getUsername(),
                postgres.getPassword(),
                postgres.getDatabaseName());
    }
}
