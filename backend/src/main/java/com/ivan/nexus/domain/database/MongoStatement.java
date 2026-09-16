package com.ivan.nexus.domain.database;

public record MongoStatement(
        String op,
        String database,
        String collection,
        String filterJson,
        String projectionJson,
        String pipelineJson,
        String documentJson,
        String updateJson,
        int limit,
        boolean multi,
        StatementClass statementClass,
        boolean requiresConfirmation
) {}
