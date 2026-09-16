package com.ivan.nexus.domain.database;

import java.util.List;

public record QueryResult(
        List<String> columns,
        List<List<Object>> rows,
        boolean truncated,
        long durationMs,
        int rowCount
) {
    public QueryResult {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
    }
}
