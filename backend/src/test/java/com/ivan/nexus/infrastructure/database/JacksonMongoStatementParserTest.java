package com.ivan.nexus.infrastructure.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonMongoStatementParserTest {
    private final JacksonMongoStatementParser parser = new JacksonMongoStatementParser(new ObjectMapper());

    @Test
    void preservesOperationForDomainClassificationAndDetectsEmptyFilter() {
        var statement = parser.parse("{\"op\":\"FiNd\",\"database\":\"app\",\"collection\":\"u\"}");

        assertEquals("FiNd", statement.op());
        assertEquals("{}", statement.filterJson());
        assertTrue(statement.emptyFilter());
        assertNull(statement.requestedLimit());
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
        assertEquals("Statement is not allowed", exception.getMessage());
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
        assertFalse(statement.emptyFilter());
        assertEquals(java.util.List.of("$match"), statement.pipelineStageOperators());
    }

    @Test
    void absentJsonFragmentsRemainNull() {
        var statement = parser.parse("{\"op\":\"insert\",\"database\":\"app\",\"collection\":\"users\"}");

        assertNull(statement.projectionJson());
        assertNull(statement.pipelineJson());
        assertNull(statement.documentJson());
        assertNull(statement.updateJson());
    }

    @Test
    void preservesRequestedLimitAndMultiFlagForDomainClassification() {
        var statement = parser.parse("""
                {"op":"delete","database":"app","collection":"users","filter":{"active":true},"limit":700,"multi":true}
                """);

        assertEquals(700, statement.requestedLimit());
        assertTrue(statement.multi());
        assertFalse(statement.emptyFilter());
    }

    @Test
    void extractsEveryPipelineStageOperator() {
        var statement = parser.parse("""
                {
                  "op":"aggregate",
                  "database":"app",
                  "collection":"users",
                  "pipeline":[
                    {"$match":{"active":true}},
                    null,
                    "ignored",
                    {"$project":{"name":1},"$sort":{"name":1}}
                  ]
                }
                """);

        assertEquals(
                java.util.List.of("$match", "$project", "$sort"),
                statement.pipelineStageOperators());
    }
}
