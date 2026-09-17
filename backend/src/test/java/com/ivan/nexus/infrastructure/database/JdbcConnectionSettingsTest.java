package com.ivan.nexus.infrastructure.database;

import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcConnectionSettingsTest {

    @Test
    void mysqlTimeoutsAreMilliseconds() {
        ResolvedTarget target = new ResolvedTarget("127.0.0.1", 3306, "lab", "lab", "lab");
        var mysql = JdbcConnectionSettings.connectionProperties(DatabaseEngine.MYSQL, target);
        assertEquals("5000", mysql.getProperty("connectTimeout"));
        assertEquals("15000", mysql.getProperty("socketTimeout"));

        var postgres = JdbcConnectionSettings.connectionProperties(DatabaseEngine.POSTGRES, target);
        assertEquals("5", postgres.getProperty("connectTimeout"));
        assertEquals("15", postgres.getProperty("socketTimeout"));
    }

    @Test
    void jdbcUrlRejectsInjectedDatabaseName() {
        ResolvedTarget poisoned = new ResolvedTarget(
                "127.0.0.1", 5432, "lab", "lab", "lab?allowMultiQueries=true");
        DomainException ex = assertThrows(
                DomainException.class,
                () -> JdbcConnectionSettings.jdbcUrl(DatabaseEngine.POSTGRES, poisoned));
        assertEquals(NexusErrorCode.QUERY_FAILED, ex.getCode());
        assertFalse(JdbcConnectionSettings.jdbcUrl(DatabaseEngine.POSTGRES, new ResolvedTarget(
                "127.0.0.1", 5432, "lab", "lab", "lab")).contains("?"));
    }
}
