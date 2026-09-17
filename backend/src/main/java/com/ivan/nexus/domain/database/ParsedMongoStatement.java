package com.ivan.nexus.domain.database;

import java.util.List;

public record ParsedMongoStatement(
        String op,
        String database,
        String collection,
        String filterJson,
        String projectionJson,
        String pipelineJson,
        String documentJson,
        String updateJson,
        Integer requestedLimit,
        boolean multi,
        boolean emptyFilter,
        List<String> pipelineStageOperators
) {
    public ParsedMongoStatement {
        pipelineStageOperators = List.copyOf(pipelineStageOperators);
    }
}
