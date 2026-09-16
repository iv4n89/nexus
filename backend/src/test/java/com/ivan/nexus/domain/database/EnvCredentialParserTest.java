package com.ivan.nexus.domain.database;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvCredentialParserTest {

    @Test
    void postgresRequiresPassword() {
        var ok = EnvCredentialParser.parse(DatabaseEngine.POSTGRES, Map.of(
                "POSTGRES_USER", "app", "POSTGRES_PASSWORD", "p", "POSTGRES_DB", "app"));
        assertTrue(ok.reachable());
        assertEquals("app", ok.username());
        var bad = EnvCredentialParser.parse(DatabaseEngine.POSTGRES, Map.of("POSTGRES_DB", "app"));
        assertFalse(bad.reachable());
    }

    @Test
    void mysqlPrefersUserThenRoot() {
        var user = EnvCredentialParser.parse(DatabaseEngine.MYSQL, Map.of(
                "MYSQL_USER", "u", "MYSQL_PASSWORD", "p", "MYSQL_DATABASE", "d"));
        assertEquals("u", user.username());
        var root = EnvCredentialParser.parse(DatabaseEngine.MYSQL, Map.of(
                "MYSQL_ROOT_PASSWORD", "r", "MYSQL_DATABASE", "d"));
        assertEquals("root", root.username());
        assertEquals("r", root.password());
    }

    @Test
    void mariadbAliases() {
        var parsed = EnvCredentialParser.parse(DatabaseEngine.MYSQL, Map.of(
                "MARIADB_USER", "u", "MARIADB_PASSWORD", "p", "MARIADB_DATABASE", "d"));
        assertTrue(parsed.reachable());
    }

    @Test
    void mongoOptionalAuth() {
        var anon = EnvCredentialParser.parse(DatabaseEngine.MONGO, Map.of());
        assertTrue(anon.reachable());
        assertEquals("test", anon.defaultDatabase());
        var half = EnvCredentialParser.parse(DatabaseEngine.MONGO, Map.of("MONGO_INITDB_ROOT_USERNAME", "root"));
        assertFalse(half.reachable());
    }
}
