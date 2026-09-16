package com.ivan.nexus.infrastructure.database;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static ResolvedTarget target() {
        return new ResolvedTarget(
                postgres.getHost(),
                postgres.getMappedPort(5432),
                postgres.getUsername(),
                postgres.getPassword(),
                postgres.getDatabaseName());
    }
}
