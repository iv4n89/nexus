package com.ivan.nexus.application.database;

import com.ivan.nexus.domain.database.ParsedMongoStatement;

public interface MongoStatementParser {
    ParsedMongoStatement parse(String json);
}
