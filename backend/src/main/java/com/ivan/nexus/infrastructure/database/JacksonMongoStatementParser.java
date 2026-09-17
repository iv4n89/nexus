package com.ivan.nexus.infrastructure.database;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.application.database.MongoStatementParser;
import com.ivan.nexus.domain.database.MongoStatement;
import com.ivan.nexus.domain.database.StatementClass;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class JacksonMongoStatementParser implements MongoStatementParser {
    private final ObjectMapper objectMapper;

    public JacksonMongoStatementParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public MongoStatement parse(String json) {
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
        op = op.toLowerCase(Locale.ROOT);
        JsonNode filterNode = root.get("filter");
        boolean emptyFilter = filterNode == null
                || filterNode.isNull()
                || (filterNode.isObject() && filterNode.isEmpty());
        String filterJson = emptyFilter ? "{}" : filterNode.toString();
        String projectionJson = jsonOrNull(root.get("projection"));
        String pipelineJson = jsonOrNull(root.get("pipeline"));
        String documentJson = jsonOrNull(root.get("document"));
        String updateJson = jsonOrNull(root.get("update"));
        int limit = root.path("limit").isNumber() ? root.get("limit").asInt() : 100;
        if (limit < 1) {
            limit = 100;
        }
        if (limit > 500) {
            limit = 500;
        }
        boolean multi = root.path("multi").asBoolean(false);

        StatementClass statementClass;
        boolean requiresConfirmation = false;
        switch (op) {
            case "find" -> statementClass = StatementClass.READ;
            case "aggregate" -> {
                if (writesDocuments(root.get("pipeline"))) {
                    throw notAllowed();
                }
                statementClass = StatementClass.READ;
            }
            case "insert" -> statementClass = StatementClass.WRITE;
            case "update" -> {
                if (emptyFilter) {
                    statementClass = StatementClass.DESTRUCTIVE;
                    requiresConfirmation = true;
                } else {
                    statementClass = StatementClass.WRITE;
                }
            }
            case "delete" -> {
                if (emptyFilter) {
                    statementClass = StatementClass.DESTRUCTIVE;
                    requiresConfirmation = true;
                } else {
                    statementClass = StatementClass.WRITE;
                    requiresConfirmation = multi;
                }
            }
            default -> throw notAllowed();
        }

        return new MongoStatement(
                op,
                database,
                collection,
                filterJson,
                projectionJson,
                pipelineJson,
                documentJson,
                updateJson,
                limit,
                multi,
                statementClass,
                requiresConfirmation);
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    private static boolean writesDocuments(JsonNode pipeline) {
        if (pipeline == null || !pipeline.isArray()) {
            return false;
        }
        for (JsonNode stage : pipeline) {
            if (stage == null || !stage.isObject()) {
                continue;
            }
            var names = stage.fieldNames();
            while (names.hasNext()) {
                String key = names.next();
                if ("$out".equalsIgnoreCase(key) || "$merge".equalsIgnoreCase(key)) {
                    return true;
                }
            }
        }
        return false;
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
