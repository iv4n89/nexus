package com.ivan.nexus.interfaces.database;

import com.ivan.nexus.infrastructure.database.JdbcQueryExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DatabaseDtosTest {

    @Test
    void fromSqlIncludesCatalogColumns() {
        var catalog = new JdbcQueryExecutor.SqlCatalog(List.of(
                new JdbcQueryExecutor.SqlTable(
                        "public",
                        "jobs",
                        "table",
                        List.of("id"),
                        List.of(new JdbcQueryExecutor.SqlColumn("id", "uuid", false),
                                new JdbcQueryExecutor.SqlColumn("name", "text", true)))));

        DatabaseDtos.MetadataResponse response = DatabaseDtos.fromSql(
                com.ivan.nexus.domain.database.DatabaseEngine.POSTGRES, catalog);

        DatabaseDtos.TableResponse table = response.schemas().getFirst().tables().getFirst();
        assertEquals("jobs", table.name());
        assertEquals(List.of("id"), table.primaryKey());
        assertEquals(2, table.columns().size());
        assertEquals("id", table.columns().getFirst().name());
        assertEquals("uuid", table.columns().getFirst().dataType());
        assertFalse(table.columns().getFirst().nullable());
    }
}
