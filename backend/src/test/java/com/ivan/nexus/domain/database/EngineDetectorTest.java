package com.ivan.nexus.domain.database;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineDetectorTest {

    @Test
    void postgresOfficial() {
        assertEquals(Optional.of(DatabaseEngine.POSTGRES), EngineDetector.fromImage("postgres:16-alpine"));
    }

    @Test
    void bitnamiPostgres() {
        assertEquals(Optional.of(DatabaseEngine.POSTGRES), EngineDetector.fromImage("bitnami/postgresql:16"));
    }

    @Test
    void mysqlAndMariadb() {
        assertEquals(Optional.of(DatabaseEngine.MYSQL), EngineDetector.fromImage("mysql:8"));
        assertEquals(Optional.of(DatabaseEngine.MYSQL), EngineDetector.fromImage("mariadb:11"));
    }

    @Test
    void mongo() {
        assertEquals(Optional.of(DatabaseEngine.MONGO), EngineDetector.fromImage("mongo:7"));
        assertEquals(Optional.of(DatabaseEngine.MONGO), EngineDetector.fromImage("bitnami/mongodb:7"));
    }

    @Test
    void ignoresApps() {
        assertTrue(EngineDetector.fromImage("nginx:alpine").isEmpty());
        assertTrue(EngineDetector.fromImage("redis:7").isEmpty());
        assertTrue(EngineDetector.fromImage("nexus-backend:latest").isEmpty());
        assertTrue(EngineDetector.fromImage("mongo-express:1.0").isEmpty());
    }
}
