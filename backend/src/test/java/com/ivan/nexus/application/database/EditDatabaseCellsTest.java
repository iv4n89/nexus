package com.ivan.nexus.application.database;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.DatabaseInstance;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EditDatabaseCellsTest {

    @Mock DiscoverProjectDatabases discover;
    @Mock SqlExecutor jdbc;
    @Mock MongoExecutor mongo;
    @Mock RecordAudit recordAudit;
    @Mock UserDirectory users;
    @InjectMocks EditDatabaseCells edit;

    @Test
    void emptyPatchesAreNotAllowed() {
        stubReady(DatabaseEngine.POSTGRES);
        DomainException ex = assertThrows(
                DomainException.class,
                () -> edit.execute(command(
                        "public", "jobs", null, null, List.of(), List.of(), List.of())));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        verify(jdbc, never()).applyCells(any(), any(), any(), any(), any());
    }

    @Test
    void patchingPrimaryKeyColumnIsNotAllowed() {
        stubReady(DatabaseEngine.POSTGRES);
        EditDatabaseCellsCommand body = command(
                "public",
                "jobs",
                null,
                null,
                List.of(new EditDatabaseCellsCommand.CellPatch(Map.of("id", 1), "id", "2", null, null)),
                List.of(),
                List.of());
        DomainException ex = assertThrows(
                DomainException.class,
                () -> edit.execute(body));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        verify(jdbc, never()).applyCells(any(), any(), any(), any(), any());
    }

    @Test
    void mongoRejectsInserts() {
        stubReady(DatabaseEngine.MONGO);
        EditDatabaseCellsCommand body = command(
                null,
                null,
                "app",
                "jobs",
                List.of(),
                List.of(new EditDatabaseCellsCommand.InsertValues(Map.of("n", 1))),
                List.of());
        DomainException ex = assertThrows(
                DomainException.class,
                () -> edit.execute(body));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        verify(mongo, never()).updateDocuments(any(), any(), any(), any());
        verify(jdbc, never()).applyCells(any(), any(), any(), any(), any());
    }

    @Test
    void insertMayIncludePrimaryKeyColumn() {
        stubReady(DatabaseEngine.POSTGRES);
        when(jdbc.applyCells(any(), any(), any(), any(), any()))
                .thenReturn(new QueryResult(List.of("updateCount"), List.of(List.of(1)), false, 1, 1));
        when(users.findIdByUsername("admin")).thenReturn(java.util.Optional.empty());
        EditDatabaseCellsCommand body = command(
                "public",
                "users",
                null,
                null,
                List.of(),
                List.of(new EditDatabaseCellsCommand.InsertValues(Map.of("id", 9, "email", "nuevo@x"))),
                List.of());
        edit.execute(body);
        verify(jdbc).applyCells(any(), any(), eq("public"), eq("users"), any());
    }

    @Test
    void commandDefensivelyCopiesMapsWithoutRejectingNullCellValues() {
        Map<String, Object> primaryKey = new LinkedHashMap<>();
        primaryKey.put("tenant", null);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", null);
        Map<String, Object> delete = new LinkedHashMap<>();
        delete.put("archivedAt", null);
        List<Map<String, Object>> deletes = new ArrayList<>();
        deletes.add(delete);

        EditDatabaseCellsCommand command = command(
                "public",
                "users",
                null,
                null,
                List.of(new EditDatabaseCellsCommand.CellPatch(primaryKey, "email", null, null, null)),
                List.of(new EditDatabaseCellsCommand.InsertValues(values)),
                deletes);
        primaryKey.put("id", 9);
        values.put("email", "changed@example.com");
        delete.put("id", 10);
        deletes.clear();

        assertEquals(1, command.patches().getFirst().primaryKey().size());
        assertNull(command.patches().getFirst().primaryKey().get("tenant"));
        assertEquals(1, command.inserts().getFirst().values().size());
        assertNull(command.inserts().getFirst().values().get("name"));
        assertEquals(1, command.deletes().size());
        assertEquals(1, command.deletes().getFirst().size());
        assertNull(command.deletes().getFirst().get("archivedAt"));
    }

    private void stubReady(DatabaseEngine engine) {
        DatabaseInstance instance = new DatabaseInstance(
                "lab:db", "lab", "abc", "db", engine, DatabaseStatus.READY, "lab");
        when(discover.resolve("lab", "lab:db"))
                .thenReturn(new InstanceResolution(instance, new ResolvedTarget("127.0.0.1", 5432, "lab", "lab", "lab")));
    }

    private static EditDatabaseCellsCommand command(
            String schema,
            String table,
            String mongoDatabase,
            String collection,
            List<EditDatabaseCellsCommand.CellPatch> patches,
            List<EditDatabaseCellsCommand.InsertValues> inserts,
            List<Map<String, Object>> deletes) {
        return new EditDatabaseCellsCommand(
                "lab",
                "lab:db",
                "ADMIN",
                "admin",
                "127.0.0.1",
                schema,
                table,
                mongoDatabase,
                collection,
                patches,
                inserts,
                deletes);
    }
}
