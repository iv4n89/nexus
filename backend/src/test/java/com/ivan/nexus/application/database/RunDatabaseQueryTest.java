package com.ivan.nexus.application.database;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.DatabaseInstance;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.domain.database.MongoStatement;
import com.ivan.nexus.domain.database.ParsedMongoStatement;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.database.StatementClass;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.user.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RunDatabaseQueryTest {

    @Mock
    DiscoverProjectDatabases discover;
    @Mock
    SqlExecutor jdbc;
    @Mock
    MongoExecutor mongo;
    @Mock
    RecordAudit recordAudit;
    @Mock
    UserJpaRepository users;
    @Mock
    MongoStatementParser mongoStatementParser;

    RunDatabaseQuery useCase;

    private final ResolvedTarget target = new ResolvedTarget("127.0.0.1", 5432, "lab", "secret", "lab");
    private final DatabaseInstance instance = new DatabaseInstance(
            "lab:aaaaaaaaaaaa",
            "lab",
            "aaaaaaaaaaaa0000",
            "db",
            DatabaseEngine.POSTGRES,
            DatabaseStatus.READY,
            "lab");

    @BeforeEach
    void setUp() {
        useCase = new RunDatabaseQuery(discover, jdbc, mongo, recordAudit, users, mongoStatementParser);
        lenient().when(discover.resolve("lab", "lab:aaaaaaaaaaaa")).thenReturn(new InstanceResolution(instance, target));
    }

    @Test
    void viewerInsertThrows() {
        DomainException ex = assertThrows(
                DomainException.class,
                () -> useCase.execute("lab", "lab:aaaaaaaaaaaa", "INSERT INTO t VALUES (1)", false, "VIEWER", "viewer", "127.0.0.1"));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        verify(jdbc, never()).query(any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void adminDeleteWithoutWhereRequiresConfirm() {
        DomainException ex = assertThrows(
                DomainException.class,
                () -> useCase.execute("lab", "lab:aaaaaaaaaaaa", "DELETE FROM users", false, "ADMIN", "admin", "127.0.0.1"));
        assertEquals(NexusErrorCode.CONFIRMATION_REQUIRED, ex.getCode());
        verify(jdbc, never()).query(any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void adminDeleteWithConfirmRuns() {
        given(jdbc.query(eq(DatabaseEngine.POSTGRES), eq(target), eq("DELETE FROM users"), eq(StatementClass.DESTRUCTIVE), eq(500)))
                .willReturn(new QueryResult(List.of("updateCount"), List.of(List.of(1)), false, 3, 1));
        given(users.findByUsername("admin")).willReturn(Optional.empty());

        QueryResult result = useCase.execute(
                "lab", "lab:aaaaaaaaaaaa", "DELETE FROM users", true, "ADMIN", "admin", "127.0.0.1");

        assertEquals(1, result.rowCount());
        verify(recordAudit).execute(any(), eq(AuditAction.DB_QUERY), eq("lab"), eq("db"), eq("127.0.0.1"), any());
    }

    @Test
    void viewerCannotQueryControlPlaneDatabase() {
        DomainException ex = assertThrows(
                DomainException.class,
                () -> useCase.execute(
                        "nexus",
                        "nexus:cccccccccccc",
                        "SELECT 1",
                        false,
                        "VIEWER",
                        "viewer",
                        "127.0.0.1"));
        assertEquals(NexusErrorCode.FORBIDDEN, ex.getCode());
        verify(jdbc, never()).query(any(), any(), any(), any(), any(Integer.class));
        verify(discover, never()).resolve("nexus", "nexus:cccccccccccc");
    }

    @Test
    void adminCanQueryControlPlaneDatabase() {
        ResolvedTarget nexusTarget = new ResolvedTarget("127.0.0.1", 5432, "nexus", "nexus", "nexus");
        DatabaseInstance nexus = new DatabaseInstance(
                "nexus:cccccccccccc",
                "nexus",
                "cccccccccccc0000",
                "postgres",
                DatabaseEngine.POSTGRES,
                DatabaseStatus.READY,
                "nexus");
        given(discover.resolve("nexus", "nexus:cccccccccccc")).willReturn(new InstanceResolution(nexus, nexusTarget));
        given(jdbc.query(eq(DatabaseEngine.POSTGRES), eq(nexusTarget), eq("SELECT 1"), eq(StatementClass.READ), eq(500)))
                .willReturn(new QueryResult(List.of("?column?"), List.of(List.of(1)), false, 1, 1));
        given(users.findByUsername("admin")).willReturn(Optional.empty());

        QueryResult result = useCase.execute(
                "nexus", "nexus:cccccccccccc", "SELECT 1", false, "ADMIN", "admin", "127.0.0.1");

        assertEquals(1, result.rowCount());
    }

    @Test
    void mongoStatementsAreParsedThroughTheApplicationPort() {
        String statement = "{\"op\":\"find\",\"database\":\"lab\",\"collection\":\"users\"}";
        DatabaseInstance mongoInstance = new DatabaseInstance(
                "lab:bbbbbbbbbbbb",
                "lab",
                "bbbbbbbbbbbb0000",
                "mongo",
                DatabaseEngine.MONGO,
                DatabaseStatus.READY,
                "lab");
        ParsedMongoStatement parsed = new ParsedMongoStatement(
                "FiNd", "lab", "users", "{}", null, null, null, null,
                null, false, true, List.of());
        MongoStatement classified = new MongoStatement(
                "find", "lab", "users", "{}", null, null, null, null,
                100, false, StatementClass.READ, false);
        given(discover.resolve("lab", "lab:bbbbbbbbbbbb")).willReturn(new InstanceResolution(mongoInstance, target));
        given(mongoStatementParser.parse(statement)).willReturn(parsed);
        given(mongo.execute(target, classified))
                .willReturn(new QueryResult(List.of("name"), List.of(List.of("Ada")), false, 2, 1));
        given(users.findByUsername("admin")).willReturn(Optional.empty());

        QueryResult result = useCase.execute(
                "lab", "lab:bbbbbbbbbbbb", statement, false, "ADMIN", "admin", "127.0.0.1");

        assertEquals(1, result.rowCount());
        verify(mongoStatementParser).parse(statement);
        verify(mongo).execute(target, classified);
    }
}
