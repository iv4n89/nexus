package com.ivan.nexus.application.database;

import com.ivan.nexus.domain.database.MongoStatement;

public interface MongoStatementParser {
    MongoStatement parse(String json);
}
