package com.ivan.nexus.infrastructure.database;

import com.ivan.nexus.domain.database.MongoStatement;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.database.StatementClass;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class MongoQueryExecutorIT {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

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
}
