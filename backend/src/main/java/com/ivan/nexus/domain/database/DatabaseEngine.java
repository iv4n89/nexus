package com.ivan.nexus.domain.database;

public enum DatabaseEngine {
    POSTGRES,
    MYSQL,
    MONGO;

    public int defaultPort() {
        return switch (this) {
            case POSTGRES -> 5432;
            case MYSQL -> 3306;
            case MONGO -> 27017;
        };
    }
}
