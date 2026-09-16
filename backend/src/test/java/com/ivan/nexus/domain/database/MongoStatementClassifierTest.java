package com.ivan.nexus.domain.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoStatementClassifierTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void findIsRead() throws Exception {
        var stmt = MongoStatementClassifier.parse(mapper, "{\"op\":\"find\",\"database\":\"app\",\"collection\":\"u\"}");
        assertEquals(StatementClass.READ, stmt.statementClass());
    }

    @Test
    void aggregateOutForbidden() {
        String json = "{\"op\":\"aggregate\",\"database\":\"a\",\"collection\":\"c\",\"pipeline\":[{\"$out\":\"x\"}]}";
        assertThrows(DomainException.class, () -> MongoStatementClassifier.parse(new ObjectMapper(), json));
    }

    @Test
    void deleteEmptyFilterIsDestructive() throws Exception {
        var stmt = MongoStatementClassifier.parse(new ObjectMapper(),
                "{\"op\":\"delete\",\"database\":\"a\",\"collection\":\"c\",\"filter\":{}}");
        assertEquals(StatementClass.DESTRUCTIVE, stmt.statementClass());
        assertTrue(stmt.requiresConfirmation());
    }

    @Test
    void unknownOpRejected() {
        assertThrows(DomainException.class,
                () -> MongoStatementClassifier.parse(new ObjectMapper(), "{\"op\":\"drop\"}"));
    }
}
