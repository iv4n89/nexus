package com.ivan.nexus.domain.database;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;

import java.util.Locale;

public final class MongoStatementClassifier {
    private MongoStatementClassifier() {}

    public static MongoStatement classify(ParsedMongoStatement input) {
        String op = input.op().toLowerCase(Locale.ROOT);
        int limit = normalizeLimit(input.requestedLimit());
        StatementClass statementClass;
        boolean requiresConfirmation = false;

        switch (op) {
            case "find" -> statementClass = StatementClass.READ;
            case "aggregate" -> {
                if (writesDocuments(input)) {
                    throw notAllowed();
                }
                statementClass = StatementClass.READ;
            }
            case "insert" -> statementClass = StatementClass.WRITE;
            case "update" -> {
                if (input.emptyFilter()) {
                    statementClass = StatementClass.DESTRUCTIVE;
                    requiresConfirmation = true;
                } else {
                    statementClass = StatementClass.WRITE;
                }
            }
            case "delete" -> {
                if (input.emptyFilter()) {
                    statementClass = StatementClass.DESTRUCTIVE;
                    requiresConfirmation = true;
                } else {
                    statementClass = StatementClass.WRITE;
                    requiresConfirmation = input.multi();
                }
            }
            default -> throw notAllowed();
        }

        return new MongoStatement(
                op,
                input.database(),
                input.collection(),
                input.filterJson(),
                input.projectionJson(),
                input.pipelineJson(),
                input.documentJson(),
                input.updateJson(),
                limit,
                input.multi(),
                statementClass,
                requiresConfirmation);
    }

    private static int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit < 1) {
            return 100;
        }
        return Math.min(requestedLimit, 500);
    }

    private static boolean writesDocuments(ParsedMongoStatement input) {
        for (String operator : input.pipelineStageOperators()) {
            if ("$out".equalsIgnoreCase(operator) || "$merge".equalsIgnoreCase(operator)) {
                return true;
            }
        }
        return false;
    }

    private static DomainException notAllowed() {
        return new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed");
    }
}
