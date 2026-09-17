package com.ivan.nexus.domain.database;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueryResultTest {

    @Test
    void allowsNullCells() {
        List<Object> row = new ArrayList<>();
        row.add(1);
        row.add(null);

        QueryResult result = assertDoesNotThrow(
                () -> new QueryResult(List.of("id", "note"), List.of(row), false, 1, 1));

        assertNull(result.rows().getFirst().get(1));
    }
}
