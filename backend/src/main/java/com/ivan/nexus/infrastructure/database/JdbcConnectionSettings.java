package com.ivan.nexus.infrastructure.database;

import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.ResolvedTarget;

import java.util.Properties;

final class JdbcConnectionSettings {
    static final int CONNECT_TIMEOUT_SECONDS = 5;
    static final int SOCKET_TIMEOUT_SECONDS = 15;
    static final int QUERY_TIMEOUT_SECONDS = 10;

    private JdbcConnectionSettings() {
    }

    static Properties connectionProperties(DatabaseEngine engine, ResolvedTarget target) {
        Properties props = new Properties();
        if (target.username() != null) {
            props.setProperty("user", JdbcQueryExecutor.safeName(target.username()));
        }
        if (target.password() != null) {
            props.setProperty("password", target.password());
        }
        if (engine == DatabaseEngine.MYSQL) {
            props.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS * 1000));
            props.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_SECONDS * 1000));
        } else {
            props.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
            props.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_SECONDS));
        }
        if (engine == DatabaseEngine.POSTGRES) {
            props.setProperty("options", "-c statement_timeout=10000");
        }
        return props;
    }

    static String jdbcUrl(DatabaseEngine engine, ResolvedTarget target) {
        String database = JdbcQueryExecutor.safeName(target.defaultDatabase());
        if (engine == DatabaseEngine.POSTGRES) {
            return "jdbc:postgresql://%s:%d/%s".formatted(target.host(), target.port(), database);
        }
        return "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true".formatted(
                target.host(), target.port(), database);
    }
}
