package com.ivan.nexus.infrastructure.database;

import com.ivan.nexus.domain.database.StatementClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void writeSelectsStillReturnAResultSet() {
        assertTrue(JdbcQueryExecutor.returnsResultSet("SELECT n FROM t FOR UPDATE", StatementClass.WRITE));
        assertTrue(JdbcQueryExecutor.returnsResultSet("WITH x AS (SELECT 1) SELECT * FROM x FOR UPDATE", StatementClass.WRITE));
        assertFalse(JdbcQueryExecutor.returnsResultSet("DELETE FROM t WHERE n = 1", StatementClass.WRITE));
    }
}
