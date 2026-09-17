package com.ivan.nexus.infrastructure.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.domain.database.StatementClass;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonMongoStatementParserTest {
    private final JacksonMongoStatementParser parser = new JacksonMongoStatementParser(new ObjectMapper());

    @Test
    void findIsReadAndOperationIsLowercased() {
        var statement = parser.parse("{\"op\":\"FiNd\",\"database\":\"app\",\"collection\":\"u\"}");

        assertEquals("find", statement.op());
        assertEquals(StatementClass.READ, statement.statementClass());
        assertEquals("{}", statement.filterJson());
        assertEquals(100, statement.limit());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json",
            "[]",
            "{\"op\":\"find\",\"database\":\"app\"}",
            "{\"op\":\"find\",\"database\":\"\",\"collection\":\"users\"}"
    })
    void malformedNonObjectOrMissingRequiredFieldsAreRejected(String json) {
        DomainException exception = assertThrows(DomainException.class, () -> parser.parse(json));

        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, exception.getCode());
    }

    @Test
    void preservesJsonFragments() {
        var statement = parser.parse("""
                {
                  "op": "update",
                  "database": "app",
                  "collection": "users",
                  "filter": {"active": true},
                  "projection": {"name": 1},
                  "pipeline": [{"$match": {"active": true}}],
                  "document": {"name": "Ada"},
                  "update": {"$set": {"active": false}}
                }
                """);

        assertEquals("{\"active\":true}", statement.filterJson());
        assertEquals("{\"name\":1}", statement.projectionJson());
        assertEquals("[{\"$match\":{\"active\":true}}]", statement.pipelineJson());
        assertEquals("{\"name\":\"Ada\"}", statement.documentJson());
        assertEquals("{\"$set\":{\"active\":false}}", statement.updateJson());
    }

    @Test
    void absentJsonFragmentsRemainNull() {
        var statement = parser.parse("{\"op\":\"insert\",\"database\":\"app\",\"collection\":\"users\"}");

        assertNull(statement.projectionJson());
        assertNull(statement.pipelineJson());
        assertNull(statement.documentJson());
        assertNull(statement.updateJson());
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
        var statement = parser.parse("""
                {"op":"find","database":"app","collection":"users","limit":%d}
                """.formatted(requested));

        assertEquals(expected, statement.limit());
    }

    @Test
    void deleteWithEmptyFilterIsDestructiveAndRequiresConfirmation() {
        var statement = parser.parse("""
                {"op":"delete","database":"app","collection":"users","filter":{}}
                """);

        assertEquals(StatementClass.DESTRUCTIVE, statement.statementClass());
        assertTrue(statement.requiresConfirmation());
    }

    @Test
    void multiDeleteRequiresConfirmation() {
        var statement = parser.parse("""
                {
                  "op":"delete",
                  "database":"app",
                  "collection":"users",
                  "filter":{"inactive":true},
                  "multi":true
                }
                """);

        assertEquals(StatementClass.WRITE, statement.statementClass());
        assertTrue(statement.multi());
        assertTrue(statement.requiresConfirmation());
    }

    @ParameterizedTest
    @ValueSource(strings = {"$out", "$merge", "$OUT", "$MERGE"})
    void aggregateWriteStagesAreRejected(String stage) {
        String json = """
                {"op":"aggregate","database":"app","collection":"users","pipeline":[{"%s":"archive"}]}
                """.formatted(stage);

        assertThrows(DomainException.class, () -> parser.parse(json));
    }

    @Test
    void aggregateDoesNotTreatOutSubstringAsWrite() {
        String json = """
                {
                  "op":"aggregate",
                  "database":"app",
                  "collection":"users",
                  "pipeline":[{"$match":{"note":"uses $out in a string"}}]
                }
                """;

        assertEquals(StatementClass.READ, parser.parse(json).statementClass());
    }

    @Test
    void unknownOperationIsRejected() {
        assertThrows(
                DomainException.class,
                () -> parser.parse("{\"op\":\"drop\",\"database\":\"app\",\"collection\":\"users\"}"));
    }
}
