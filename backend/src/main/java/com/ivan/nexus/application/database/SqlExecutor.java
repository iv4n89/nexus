package com.ivan.nexus.application.database;

import com.ivan.nexus.domain.database.CellPatchGrouper;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.database.StatementClass;

import java.util.List;

public interface SqlExecutor {
    QueryResult query(
            DatabaseEngine engine,
            ResolvedTarget target,
            String sql,
            StatementClass statementClass,
            int limit);

    QueryResult preview(DatabaseEngine engine, ResolvedTarget target, String schema, String table);

    QueryResult applyCells(
            DatabaseEngine engine,
            ResolvedTarget target,
            String schema,
            String table,
            CellPatchGrouper.SqlWriteBatch batch);

    SqlCatalog metadata(DatabaseEngine engine, ResolvedTarget target);

    record SqlCatalog(List<SqlTable> tables) {}

    record SqlTable(String schema, String name, String type, List<String> primaryKey, List<SqlColumn> columns) {}

    record SqlColumn(String name, String dataType, boolean nullable) {}
}
