package com.ivan.nexus.infrastructure.database;

import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.database.StatementClass;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcQueryExecutorTest {

    @Test
    void truncatesLongTextAndBinaryCells() {
        String longText = "x".repeat(JdbcQueryExecutor.MAX_CELL_CHARS + 8);
        String truncated = (String) JdbcQueryExecutor.cell(longText);
        assertEquals(JdbcQueryExecutor.MAX_CELL_CHARS, truncated.length());
        assertTrue(truncated.endsWith("…"));

        assertEquals("<binary 4 bytes>", JdbcQueryExecutor.cell(new byte[] {1, 2, 3, 4}));
        assertEquals(42, JdbcQueryExecutor.cell(42));
    }

    @Test
    void mysqlTimeoutsAreMilliseconds() {
        ResolvedTarget target = new ResolvedTarget("127.0.0.1", 3306, "lab", "lab", "lab");
        var mysql = JdbcQueryExecutor.connectionProperties(DatabaseEngine.MYSQL, target);
        assertEquals("5000", mysql.getProperty("connectTimeout"));
        assertEquals("15000", mysql.getProperty("socketTimeout"));

        var postgres = JdbcQueryExecutor.connectionProperties(DatabaseEngine.POSTGRES, target);
        assertEquals("5", postgres.getProperty("connectTimeout"));
        assertEquals("15", postgres.getProperty("socketTimeout"));
    }

    @Test
    void jdbcUrlRejectsInjectedDatabaseName() {
        ResolvedTarget poisoned = new ResolvedTarget(
                "127.0.0.1", 5432, "lab", "lab", "lab?allowMultiQueries=true");
        DomainException ex = assertThrows(
                DomainException.class,
                () -> JdbcQueryExecutor.jdbcUrl(DatabaseEngine.POSTGRES, poisoned));
        assertEquals(NexusErrorCode.QUERY_FAILED, ex.getCode());
        assertFalse(JdbcQueryExecutor.jdbcUrl(DatabaseEngine.POSTGRES, new ResolvedTarget(
                "127.0.0.1", 5432, "lab", "lab", "lab")).contains("?"));
    }

    @Test
    void writeSelectsStillReturnAResultSet() {
        assertTrue(JdbcQueryExecutor.returnsResultSet("SELECT n FROM t FOR UPDATE", StatementClass.WRITE));
        assertTrue(JdbcQueryExecutor.returnsResultSet("WITH x AS (SELECT 1) SELECT * FROM x FOR UPDATE", StatementClass.WRITE));
        assertFalse(JdbcQueryExecutor.returnsResultSet("DELETE FROM t WHERE n = 1", StatementClass.WRITE));
    }
}
