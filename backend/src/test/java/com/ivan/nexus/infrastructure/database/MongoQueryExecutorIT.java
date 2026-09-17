package com.ivan.nexus.infrastructure.database;

import com.ivan.nexus.domain.database.CellPatchGrouper;
import com.ivan.nexus.domain.database.MongoStatement;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.database.StatementClass;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MongoQueryExecutorIT {

    @Container
    static GenericContainer<?> mongo = new GenericContainer<>("mongo:7")
            .withExposedPorts(27017)
            .waitingFor(Wait.forLogMessage("(?i).*waiting for connections.*", 1));

    private final MongoQueryExecutor executor = new MongoQueryExecutor();

    @Test
    void findReturnsIdColumn() {
        ResolvedTarget target = new ResolvedTarget(mongo.getHost(), mongo.getMappedPort(27017), null, null, "app");
        MongoStatement insert = new MongoStatement(
                "insert",
                "app",
                "users",
                "{}",
                null,
                null,
                new Document("email", "a@b.c").toJson(),
                null,
                100,
                false,
                StatementClass.WRITE,
                false);
        executor.execute(target, insert);
        executor.execute(target, new MongoStatement(
                "insert",
                "app",
                "users",
                "{}",
                null,
                null,
                new Document("email", "c@d.e").toJson(),
                null,
                100,
                false,
                StatementClass.WRITE,
                false));

        QueryResult result = executor.execute(target, new MongoStatement(
                "find",
                "app",
                "users",
                "{}",
                null,
                null,
                null,
                null,
                100,
                false,
                StatementClass.READ,
                false));
        assertTrue(result.columns().contains("_id"));
        assertTrue(result.rowCount() >= 2);
    }

    @Test
    void twoDocumentSaveWithoutReplicaSetWritesNothing() {
        ResolvedTarget target = new ResolvedTarget(mongo.getHost(), mongo.getMappedPort(27017), null, null, "app");
        executor.execute(target, insert("jobs", new Document("_id", "a").append("status", "queued")));
        executor.execute(target, insert("jobs", new Document("_id", "b").append("status", "queued")));
        List<CellPatchGrouper.MongoPatch> patches = List.of(
                new CellPatchGrouper.MongoPatch("a", "status", "running"),
                new CellPatchGrouper.MongoPatch("b", "status", "running"));
        DomainException ex = assertThrows(
                DomainException.class,
                () -> executor.updateDocuments(
                        target,
                        "app",
                        "jobs",
                        CellPatchGrouper.groupMongo(patches)));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        QueryResult found = executor.execute(target, new MongoStatement(
                "find", "app", "jobs", "{}", null, null, null, null, 100, false, StatementClass.READ, false));
        assertTrue(found.rows().stream().allMatch(row -> "queued".equals(String.valueOf(row.get(found.columns().indexOf("status"))))));
    }

    private static MongoStatement insert(String collection, Document document) {
        return new MongoStatement(
                "insert", "app", collection, "{}", null, null, document.toJson(), null, 100, false, StatementClass.WRITE, false);
    }
}
