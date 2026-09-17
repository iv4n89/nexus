package com.ivan.nexus.domain.database;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CellPatchGrouperTest {

    @Test
    void groupsTwoColumnsOfOnePrimaryKey() {
        List<CellPatchGrouper.SqlPatch> patches = List.of(
                new CellPatchGrouper.SqlPatch(Map.of("id", 1), "status", "running"),
                new CellPatchGrouper.SqlPatch(Map.of("id", 1), "name", "ingest-v2"));
        List<CellPatchGrouper.GroupedSqlUpdate> grouped = CellPatchGrouper.groupSql(patches);
        assertEquals(1, grouped.size());
        assertEquals(Map.of("id", 1), grouped.getFirst().primaryKey());
        assertEquals("running", grouped.getFirst().columns().get("status"));
        assertEquals("ingest-v2", grouped.getFirst().columns().get("name"));
        assertEquals(List.of("status", "name"), List.copyOf(grouped.getFirst().columns().keySet()));
    }

    @Test
    void laterPatchWinsSameColumn() {
        List<CellPatchGrouper.SqlPatch> patches = List.of(
                new CellPatchGrouper.SqlPatch(Map.of("id", 1), "status", "queued"),
                new CellPatchGrouper.SqlPatch(Map.of("id", 1), "status", "running"));
        assertEquals("running", CellPatchGrouper.groupSql(patches).getFirst().columns().get("status"));
    }

    @Test
    void emptyPatchesAreNotAllowed() {
        DomainException ex = assertThrows(DomainException.class, () -> CellPatchGrouper.groupSql(List.of()));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void moreThanOneHundredRowsAreNotAllowed() {
        java.util.ArrayList<CellPatchGrouper.SqlPatch> patches = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            patches.add(new CellPatchGrouper.SqlPatch(Map.of("id", i), "n", 1));
        }
        DomainException ex = assertThrows(DomainException.class, () -> CellPatchGrouper.groupSql(patches));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void planSqlOrdersDeletesThenUpdatesThenInsertsAndSkipsPatchedDeletes() {
        CellPatchGrouper.SqlWriteBatch batch = CellPatchGrouper.planSql(
                List.of(
                        new CellPatchGrouper.SqlPatch(Map.of("id", 2), "role", "ADMIN"),
                        new CellPatchGrouper.SqlPatch(Map.of("id", 3), "role", "VIEWER")),
                List.of(new CellPatchGrouper.SqlInsert(Map.of("email", "nuevo@x"))),
                List.of(Map.of("id", 3)));
        assertEquals(1, batch.deletes().size());
        assertEquals(Map.of("id", 3), batch.deletes().getFirst().primaryKey());
        assertEquals(1, batch.updates().size());
        assertEquals(Map.of("id", 2), batch.updates().getFirst().primaryKey());
        assertEquals(1, batch.inserts().size());
        assertEquals("nuevo@x", batch.inserts().getFirst().values().get("email"));
    }

    @Test
    void planSqlAllowsEmptyPatchesWhenInsertsExist() {
        CellPatchGrouper.SqlWriteBatch batch = CellPatchGrouper.planSql(
                List.of(),
                List.of(new CellPatchGrouper.SqlInsert(Map.of())),
                List.of());
        assertEquals(1, batch.inserts().size());
        assertEquals(Map.of(), batch.inserts().getFirst().values());
    }

    @Test
    void planSqlRejectsEmptyBatch() {
        DomainException ex = assertThrows(
                DomainException.class, () -> CellPatchGrouper.planSql(List.of(), List.of(), List.of()));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void planSqlRejectsMoreThanOneHundredAffectedRows() {
        java.util.ArrayList<CellPatchGrouper.SqlInsert> inserts = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            inserts.add(new CellPatchGrouper.SqlInsert(Map.of("n", i)));
        }
        DomainException ex = assertThrows(
                DomainException.class, () -> CellPatchGrouper.planSql(List.of(), inserts, List.of()));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void planSqlRejectsEmptyDeleteMap() {
        DomainException ex = assertThrows(
                DomainException.class,
                () -> CellPatchGrouper.planSql(List.of(), List.of(), List.of(Map.of())));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
    }
}
