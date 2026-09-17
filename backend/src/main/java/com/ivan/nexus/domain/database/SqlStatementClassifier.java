package com.ivan.nexus.domain.database;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlStatementClassifier {
    private static final Pattern LEADING_KEYWORD = Pattern.compile("^[A-Za-z]+");
    private static final Pattern INTO = Pattern.compile("\\bINTO\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOR_UPDATE = Pattern.compile("\\bFOR\\s+UPDATE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOR_SHARE = Pattern.compile("\\bFOR\\s+SHARE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHERE = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WITH_MUTATION =
            Pattern.compile("\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE)\\b", Pattern.CASE_INSENSITIVE);

    private SqlStatementClassifier() {}

    public static StatementClass classify(String sql) {
        if (sql == null) {
            throw notAllowed();
        }
        String cleaned = stripComments(sql).trim();
        if (cleaned.isEmpty()) {
            throw notAllowed();
        }
        int semicolon = cleaned.indexOf(';');
        if (semicolon >= 0 && !cleaned.substring(semicolon + 1).trim().isEmpty()) {
            throw notAllowed();
        }
        if (semicolon >= 0) {
            cleaned = cleaned.substring(0, semicolon).trim();
        }
        Matcher keyword = LEADING_KEYWORD.matcher(cleaned);
        if (!keyword.find()) {
            throw notAllowed();
        }
        String token = keyword.group().toUpperCase(Locale.ROOT);
        if ("WITH".equals(token)) {
            Matcher mutation = WITH_MUTATION.matcher(cleaned);
            if (mutation.find()) {
                token = mutation.group().toUpperCase(Locale.ROOT);
            } else {
                return StatementClass.READ;
            }
        }
        return classifyKeyword(token, cleaned);
    }

    private static StatementClass classifyKeyword(String keyword, String sql) {
        return switch (keyword) {
            case "SELECT" -> {
                if (INTO.matcher(sql).find() || FOR_UPDATE.matcher(sql).find() || FOR_SHARE.matcher(sql).find()) {
                    yield StatementClass.WRITE;
                }
                yield StatementClass.READ;
            }
            case "INSERT", "CREATE", "ALTER", "GRANT", "REVOKE", "COMMENT" -> StatementClass.WRITE;
            case "DROP", "TRUNCATE" -> StatementClass.DESTRUCTIVE;
            case "DELETE", "UPDATE" -> WHERE.matcher(sql).find() ? StatementClass.WRITE : StatementClass.DESTRUCTIVE;
            default -> throw notAllowed();
        };
    }

    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"') {
                i = copyQuoted(sql, i, c, out);
            } else if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int newline = sql.indexOf('\n', i);
                if (newline < 0) {
                    break;
                }
                out.append(' ');
                i = newline;
            } else if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) {
                    break;
                }
                out.append(' ');
                i = end + 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int copyQuoted(String sql, int start, char quote, StringBuilder out) {
        out.append(quote);
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            out.append(c);
            i++;
            if (c == quote) {
                if (i < sql.length() && sql.charAt(i) == quote) {
                    out.append(quote);
                    i++;
                    continue;
                }
                return i;
            }
        }
        return i;
    }

    private static DomainException notAllowed() {
        return new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed");
    }
}
