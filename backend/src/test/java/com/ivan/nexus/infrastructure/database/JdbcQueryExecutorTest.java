package com.ivan.nexus.infrastructure.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
