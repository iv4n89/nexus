package com.ivan.nexus.domain.database;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoStatementClassifierTest {
    @Test
    void findIsReadAndDefaultsLimit() {
        MongoStatement statement = MongoStatementClassifier.classify(input("FiNd", false, null, false, List.of()));

        assertEquals("find", statement.op());
        assertEquals(StatementClass.READ, statement.statementClass());
        assertEquals(100, statement.limit());
    }

    @Test
    void insertIsWrite() {
        assertEquals(
                StatementClass.WRITE,
                MongoStatementClassifier.classify(input("insert", true, 10, false, List.of())).statementClass());
    }

    @Test
    void updateWithEmptyFilterIsDestructiveAndRequiresConfirmation() {
        MongoStatement statement =
                MongoStatementClassifier.classify(input("update", true, 10, false, List.of()));

        assertEquals(StatementClass.DESTRUCTIVE, statement.statementClass());
        assertTrue(statement.requiresConfirmation());
    }

    @Test
    void updateWithFilterIsWriteWithoutConfirmation() {
        MongoStatement statement =
                MongoStatementClassifier.classify(input("update", false, 10, false, List.of()));

        assertEquals(StatementClass.WRITE, statement.statementClass());
        assertFalse(statement.requiresConfirmation());
    }

    @Test
    void multiDeleteWithFilterRequiresConfirmation() {
        MongoStatement statement =
                MongoStatementClassifier.classify(input("delete", false, 10, true, List.of()));

        assertEquals(StatementClass.WRITE, statement.statementClass());
        assertTrue(statement.requiresConfirmation());
    }

    @Test
    void singleDeleteWithFilterDoesNotRequireConfirmation() {
        MongoStatement statement =
                MongoStatementClassifier.classify(input("delete", false, 10, false, List.of()));

        assertEquals(StatementClass.WRITE, statement.statementClass());
        assertFalse(statement.requiresConfirmation());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 100",
            "-1, 100",
            "1, 1",
            "500, 500",
            "501, 500"
    })
    void defaultsAndClampsLimit(int requested, int expected) {
        MongoStatement statement =
                MongoStatementClassifier.classify(input("find", false, requested, false, List.of()));

        assertEquals(expected, statement.limit());
    }

    @ParameterizedTest
    @ValueSource(strings = {"$out", "$merge", "$OUT", "$MERGE"})
    void aggregateWriteStagesAreRejected(String stageOperator) {
        ParsedMongoStatement input = input("aggregate", false, 10, false, List.of(stageOperator));

        DomainException exception =
                assertThrows(DomainException.class, () -> MongoStatementClassifier.classify(input));

        assertNotAllowed(exception);
    }

    @Test
    void aggregateReadStagesRemainRead() {
        MongoStatement statement = MongoStatementClassifier.classify(
                input("aggregate", false, 10, false, List.of("$match", "$project")));

        assertEquals(StatementClass.READ, statement.statementClass());
    }

    @Test
    void unknownOperationIsRejectedWithExistingError() {
        DomainException exception = assertThrows(
                DomainException.class,
                () -> MongoStatementClassifier.classify(input("drop", false, 10, false, List.of())));

        assertNotAllowed(exception);
    }

    private static ParsedMongoStatement input(
            String op,
            boolean emptyFilter,
            Integer requestedLimit,
            boolean multi,
            List<String> pipelineStageOperators) {
        return new ParsedMongoStatement(
                op,
                "app",
                "users",
                emptyFilter ? "{}" : "{\"active\":true}",
                "{\"name\":1}",
                "[{\"$match\":{\"active\":true}}]",
                "{\"name\":\"Ada\"}",
                "{\"$set\":{\"active\":false}}",
                requestedLimit,
                multi,
                emptyFilter,
                pipelineStageOperators);
    }

    private static void assertNotAllowed(DomainException exception) {
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, exception.getCode());
        assertEquals("Statement is not allowed", exception.getMessage());
    }
}
