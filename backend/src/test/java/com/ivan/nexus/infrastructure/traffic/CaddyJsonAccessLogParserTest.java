package com.ivan.nexus.infrastructure.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.domain.traffic.HttpAccessEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaddyJsonAccessLogParserTest {
    private final CaddyJsonAccessLogParser parser = new CaddyJsonAccessLogParser(new ObjectMapper());

    @Test
    void parsesHappyPathJsonAccessLogLine() {
        String line = """
                {"level":"info","ts":1.0,"request":{"host":"app.example.com","headers":{"Host":["app.example.com"]}},"status":200,"size":1234,"duration":0.025}
                """;

        Optional<HttpAccessEvent> event = parser.parse(line);

        assertTrue(event.isPresent());
        assertEquals("app.example.com", event.get().host());
        assertEquals(200, event.get().status());
        assertEquals(1234, event.get().bytesOut());
        assertEquals(25, event.get().latencyMs());
    }

    @Test
    void skipsConsoleLineWithoutJsonPayload() {
        Optional<HttpAccessEvent> event = parser.parse("2026-09-18T10:31:00.000000000Z INFO serving");

        assertTrue(event.isEmpty());
    }

    @Test
    void skipsWhenHostIsMissing() {
        String line = """
                {"request":{"headers":{}},"status":200,"size":1,"duration":0.01}
                """;

        Optional<HttpAccessEvent> event = parser.parse(line);

        assertTrue(event.isEmpty());
    }

    @Test
    void stripsDockerTimestampPrefixBeforeParsing() {
        String line = """
                2026-09-18T10:31:00.000000000Z {"request":{"host":"app.example.com"},"status":200,"size":1,"duration":0.01}
                """;

        Optional<HttpAccessEvent> event = parser.parse(line);

        assertTrue(event.isPresent());
        assertEquals("app.example.com", event.get().host());
        assertEquals(200, event.get().status());
        assertEquals(1, event.get().bytesOut());
        assertEquals(10, event.get().latencyMs());
    }

    @Test
    void resolvesHostFromHeadersWhenRequestHostIsAbsent() {
        String line = """
                {"request":{"headers":{"Host":["lab.example.com"]}},"status":404,"size":10,"duration":0.001}
                """;

        Optional<HttpAccessEvent> event = parser.parse(line);

        assertTrue(event.isPresent());
        assertEquals("lab.example.com", event.get().host());
        assertEquals(404, event.get().status());
        assertEquals(10, event.get().bytesOut());
        assertEquals(1, event.get().latencyMs());
    }

    @Test
    void defaultsBytesOutToZeroWhenSizeIsMissing() {
        String line = """
                {"request":{"host":"app.example.com"},"status":200,"duration":0.005}
                """;

        Optional<HttpAccessEvent> event = parser.parse(line);

        assertTrue(event.isPresent());
        assertEquals(0, event.get().bytesOut());
        assertEquals(5, event.get().latencyMs());
    }
}
