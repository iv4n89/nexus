package com.ivan.nexus.application.database;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.DatabaseInstance;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.database.JdbcQueryExecutor;
import com.ivan.nexus.infrastructure.database.MongoQueryExecutor;
import com.ivan.nexus.infrastructure.persistence.user.UserJpaRepository;
import com.ivan.nexus.interfaces.database.DatabaseDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EditDatabaseCellsTest {

    @Mock DiscoverProjectDatabases discover;
    @Mock JdbcQueryExecutor jdbc;
    @Mock MongoQueryExecutor mongo;
    @Mock RecordAudit recordAudit;
    @Mock UserJpaRepository users;
    @InjectMocks EditDatabaseCells edit;

    @Test
    void emptyPatchesAreNotAllowed() {
        stubReady(DatabaseEngine.POSTGRES);
        DomainException ex = assertThrows(
                DomainException.class,
                () -> edit.execute("lab", "lab:db", body(List.of()), "ADMIN", "admin", "127.0.0.1"));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        verify(jdbc, never()).applyCells(any(), any(), any(), any(), any());
    }

    @Test
    void patchingPrimaryKeyColumnIsNotAllowed() {
        stubReady(DatabaseEngine.POSTGRES);
        DatabaseDtos.CellsRequest body = new DatabaseDtos.CellsRequest(
                "public",
                "jobs",
                null,
                null,
                List.of(new DatabaseDtos.CellPatch(Map.of("id", 1), "id", "2", null, null)),
                List.of(),
                List.of());
        DomainException ex = assertThrows(
                DomainException.class,
                () -> edit.execute("lab", "lab:db", body, "ADMIN", "admin", "127.0.0.1"));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        verify(jdbc, never()).applyCells(any(), any(), any(), any(), any());
    }

    @Test
    void mongoRejectsInserts() {
        stubReady(DatabaseEngine.MONGO);
        DatabaseDtos.CellsRequest body = new DatabaseDtos.CellsRequest(
                null,
                null,
                "app",
                "jobs",
                List.of(),
                List.of(new DatabaseDtos.SqlInsertValues(Map.of("n", 1))),
                List.of());
        DomainException ex = assertThrows(
                DomainException.class,
                () -> edit.execute("lab", "lab:db", body, "ADMIN", "admin", "127.0.0.1"));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        verify(mongo, never()).updateDocuments(any(), any(), any(), any());
        verify(jdbc, never()).applyCells(any(), any(), any(), any(), any());
    }

    @Test
    void insertMayIncludePrimaryKeyColumn() {
        stubReady(DatabaseEngine.POSTGRES);
        when(jdbc.applyCells(any(), any(), any(), any(), any()))
                .thenReturn(new QueryResult(List.of("updateCount"), List.of(List.of(1)), false, 1, 1));
        when(users.findByUsername("admin")).thenReturn(java.util.Optional.empty());
        DatabaseDtos.CellsRequest body = new DatabaseDtos.CellsRequest(
                "public",
                "users",
                null,
                null,
                List.of(),
                List.of(new DatabaseDtos.SqlInsertValues(Map.of("id", 9, "email", "nuevo@x"))),
                List.of());
        edit.execute("lab", "lab:db", body, "ADMIN", "admin", "127.0.0.1");
        verify(jdbc).applyCells(any(), any(), eq("public"), eq("users"), any());
    }

    private void stubReady(DatabaseEngine engine) {
        DatabaseInstance instance = new DatabaseInstance(
                "lab:db", "lab", "abc", "db", engine, DatabaseStatus.READY, "lab");
        when(discover.resolve("lab", "lab:db"))
                .thenReturn(new InstanceResolution(instance, new ResolvedTarget("127.0.0.1", 5432, "lab", "lab", "lab")));
    }

    private static DatabaseDtos.CellsRequest body(List<DatabaseDtos.CellPatch> patches) {
        return new DatabaseDtos.CellsRequest("public", "jobs", null, null, patches, List.of(), List.of());
    }
}
