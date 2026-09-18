package com.ivan.nexus.infrastructure.traffic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.domain.traffic.HttpAccessEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class CaddyJsonAccessLogParser {
    private static final Pattern DOCKER_TIMESTAMP_PREFIX =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z ");

    private final ObjectMapper objectMapper;

    public CaddyJsonAccessLogParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<HttpAccessEvent> parse(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String payload = stripDockerTimestamp(line.trim());
        if (!payload.startsWith("{")) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String host = resolveHost(root);
            if (host == null || host.isBlank()) {
                return Optional.empty();
            }
            JsonNode statusNode = root.get("status");
            if (statusNode == null || statusNode.isNull()) {
                return Optional.empty();
            }
            int status = statusNode.asInt();
            long bytesOut = root.hasNonNull("size") ? root.get("size").asLong() : 0;
            long latencyMs = root.hasNonNull("duration") && root.get("duration").isNumber()
                    ? Math.round(root.get("duration").asDouble() * 1000)
                    : 0;
            return Optional.of(new HttpAccessEvent(host, status, bytesOut, latencyMs));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static String stripDockerTimestamp(String line) {
        var matcher = DOCKER_TIMESTAMP_PREFIX.matcher(line);
        return matcher.lookingAt() ? line.substring(matcher.end()) : line;
    }

    private static String resolveHost(JsonNode root) {
        JsonNode request = root.path("request");
        String host = text(request, "host");
        if (host != null) {
            return host;
        }
        JsonNode hostHeader = request.path("headers").path("Host");
        if (hostHeader.isArray() && !hostHeader.isEmpty()) {
            return text(hostHeader.get(0));
        }
        return null;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    private static String text(JsonNode node, String field) {
        return text(node.get(field));
    }
}
