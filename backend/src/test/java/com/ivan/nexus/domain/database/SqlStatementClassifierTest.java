package com.ivan.nexus.domain.database;

import com.ivan.nexus.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlStatementClassifierTest {

    @Test
    void selectIsRead() {
        assertEquals(StatementClass.READ, SqlStatementClassifier.classify("SELECT 1"));
    }

    @Test
    void withSelectIsRead() {
        assertEquals(StatementClass.READ, SqlStatementClassifier.classify("WITH x AS (SELECT 1) SELECT * FROM x"));
    }

    @Test
    void selectIntoIsWrite() {
        assertEquals(StatementClass.WRITE, SqlStatementClassifier.classify("SELECT * INTO tmp FROM users"));
    }

    @Test
    void deleteWithWhereIsWrite() {
        assertEquals(StatementClass.WRITE, SqlStatementClassifier.classify("DELETE FROM users WHERE id = 1"));
    }

    @Test
    void deleteWithoutWhereIsDestructive() {
        assertEquals(StatementClass.DESTRUCTIVE, SqlStatementClassifier.classify("DELETE FROM users"));
    }

    @Test
    void dropIsDestructive() {
        assertEquals(StatementClass.DESTRUCTIVE, SqlStatementClassifier.classify("DROP TABLE users"));
    }

    @Test
    void rejectsSecondStatement() {
        assertThrows(DomainException.class, () -> SqlStatementClassifier.classify("SELECT 1; DELETE FROM users"));
    }
}
