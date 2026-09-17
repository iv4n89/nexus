package com.ivan.nexus.infrastructure.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.application.database.MongoStatementParser;
import com.ivan.nexus.domain.database.ParsedMongoStatement;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JacksonMongoStatementParser implements MongoStatementParser {
    private final ObjectMapper objectMapper;

    public JacksonMongoStatementParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ParsedMongoStatement parse(String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception ex) {
            throw notAllowed();
        }
        if (root == null || !root.isObject()) {
            throw notAllowed();
        }
        String op = text(root, "op");
        String database = text(root, "database");
        String collection = text(root, "collection");
        if (op == null || database == null || collection == null) {
            throw notAllowed();
        }
        JsonNode filterNode = root.get("filter");
        boolean emptyFilter = filterNode == null
                || filterNode.isNull()
                || (filterNode.isObject() && filterNode.isEmpty());
        String filterJson = emptyFilter ? "{}" : filterNode.toString();
        String projectionJson = jsonOrNull(root.get("projection"));
        String pipelineJson = jsonOrNull(root.get("pipeline"));
        String documentJson = jsonOrNull(root.get("document"));
        String updateJson = jsonOrNull(root.get("update"));
        Integer requestedLimit = root.path("limit").isNumber() ? root.get("limit").asInt() : null;
        boolean multi = root.path("multi").asBoolean(false);

        return new ParsedMongoStatement(
                op,
                database,
                collection,
                filterJson,
                projectionJson,
                pipelineJson,
                documentJson,
                updateJson,
                requestedLimit,
                multi,
                emptyFilter,
                pipelineStageOperators(root.get("pipeline")));
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    private static List<String> pipelineStageOperators(JsonNode pipeline) {
        List<String> operators = new ArrayList<>();
        if (pipeline == null || !pipeline.isArray()) {
            return operators;
        }
        for (JsonNode stage : pipeline) {
            if (stage == null || !stage.isObject()) {
                continue;
            }
            var names = stage.fieldNames();
            while (names.hasNext()) {
                operators.add(names.next());
            }
        }
        return operators;
    }

    private static String jsonOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.toString();
    }

    private static DomainException notAllowed() {
        return new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed");
    }
}
