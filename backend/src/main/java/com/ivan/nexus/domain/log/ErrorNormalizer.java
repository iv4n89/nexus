package com.ivan.nexus.domain.log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ErrorNormalizer {
    private static final Pattern ERROR_LINE = Pattern.compile(
            "(?i)(ERROR|Exception|Traceback|FATAL|failed|connection refused|timeout|HTTP 500)");
    private static final Pattern STACK_FRAME = Pattern.compile("^\\s+at\\s+\\S+\\(");
    private static final Pattern SPRING_INFO = Pattern.compile("\\sINFO\\s");
    private static final Pattern JSON_INFO_LEVEL = Pattern.compile("\"level\"\\s*:\\s*\"info\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern BIND_ADDRESS_IN_USE = Pattern.compile("bind\\(\\) to .* failed \\(98: Address in use\\)");
    private static final Pattern INFRA_NOISE = Pattern.compile(
            "MongoSocketOpenException|AsyncRequestNotUsableException|^\\s*Caused by: java\\.net\\.ConnectException: Connection refused\\s*$");
    private static final Pattern ISO_TIMESTAMP = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})?");
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern CLOCK_TIME = Pattern.compile("\\b\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\.\\d+)?\\b");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{4,}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ErrorNormalizer() {
    }

    public static Optional<ErrorFingerprint> normalize(String line) {
        if (line == null || STACK_FRAME.matcher(line).find()) {
            return Optional.empty();
        }
        if (SPRING_INFO.matcher(line).find() || JSON_INFO_LEVEL.matcher(line).find()) {
            return Optional.empty();
        }
        if (BIND_ADDRESS_IN_USE.matcher(line).find() || INFRA_NOISE.matcher(line).find()) {
            return Optional.empty();
        }
        if (!ERROR_LINE.matcher(line).find()) {
            return Optional.empty();
        }
        String normalized = ISO_TIMESTAMP.matcher(line).replaceAll("<TS>");
        normalized = UUID.matcher(normalized).replaceAll("<UUID>");
        normalized = IPV4.matcher(normalized).replaceAll("<IP>");
        normalized = CLOCK_TIME.matcher(normalized).replaceAll("<TS>");
        normalized = LONG_DIGITS.matcher(normalized).replaceAll("<NUM>");
        normalized = WHITESPACE.matcher(normalized).replaceAll(" ").trim();
        return Optional.of(new ErrorFingerprint(sha256Hex(normalized), normalized, line));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
