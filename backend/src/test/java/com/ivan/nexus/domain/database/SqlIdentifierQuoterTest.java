package com.ivan.nexus.domain.database;

import com.ivan.nexus.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlIdentifierQuoterTest {

    @Test
    void quotesPostgresIdentifiers() {
        assertEquals("\"users\"", SqlIdentifierQuoter.quote(DatabaseEngine.POSTGRES, "users"));
        assertEquals("\"weird\"\"name\"", SqlIdentifierQuoter.quote(DatabaseEngine.POSTGRES, "weird\"name"));
    }

    @Test
    void quotesMysqlIdentifiers() {
        assertEquals("`users`", SqlIdentifierQuoter.quote(DatabaseEngine.MYSQL, "users"));
        assertEquals("`weird``name`", SqlIdentifierQuoter.quote(DatabaseEngine.MYSQL, "weird`name"));
    }

    @Test
    void rejectsNul() {
        assertThrows(DomainException.class, () -> SqlIdentifierQuoter.quote(DatabaseEngine.POSTGRES, "x\0y"));
    }
}
