package com.ivan.nexus.infrastructure.database;

import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.database.StatementClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Testcontainers
class JdbcQueryExecutorMysqlIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("lab")
            .withUsername("lab")
            .withPassword("lab");

    private final JdbcQueryExecutor executor = new JdbcQueryExecutor();

    @BeforeAll
    static void schema() throws Exception {
        try (Connection conn = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t (n INT PRIMARY KEY)");
            stmt.execute("INSERT INTO t (n) VALUES (1), (2)");
        }
    }

    @Test
    void previewConnectsWithSecondScaleTimeouts() {
        QueryResult result = executor.preview(DatabaseEngine.MYSQL, target(), "lab", "t");
        assertEquals(2, result.rowCount());
        assertFalse(result.truncated());
    }

    @Test
    void previewReadsReservedTableName() throws Exception {
        try (Connection conn = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE `order` (n INT PRIMARY KEY, note JSON)");
            stmt.execute("INSERT INTO `order` (n, note) VALUES (1, '{\"ok\":true}')");
        }
        QueryResult result = executor.preview(DatabaseEngine.MYSQL, target(), "lab", "order");
        assertEquals(1, result.rowCount());
        assertEquals(1, ((Number) result.rows().getFirst().getFirst()).intValue());
    }

    @Test
    void selectWorks() {
        QueryResult result = executor.query(
                DatabaseEngine.MYSQL,
                target(),
                "SELECT n FROM t ORDER BY n",
                StatementClass.READ,
                500);
        assertEquals(2, result.rowCount());
        assertEquals(1, ((Number) result.rows().getFirst().getFirst()).intValue());
    }

    private static ResolvedTarget target() {
        return new ResolvedTarget(
                mysql.getHost(),
                mysql.getMappedPort(3306),
                mysql.getUsername(),
                mysql.getPassword(),
                mysql.getDatabaseName());
    }
}
