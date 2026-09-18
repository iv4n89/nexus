package com.ivan.nexus.infrastructure.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.ivan.nexus.application.log.LogProvider;
import com.ivan.nexus.application.traffic.ResolveTrafficTarget;
import com.ivan.nexus.application.traffic.TrafficIngestor;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CaddyAccessLogFollowerTest {

    @Mock
    private DockerClient dockerClient;
    @Mock
    private LogProvider logProvider;
    @Mock
    private ResolveTrafficTarget resolve;
    @Mock
    private TrafficIngestor ingestor;

    private CaddyJsonAccessLogParser parser;

    @BeforeEach
    void setUp() {
        parser = new CaddyJsonAccessLogParser(new ObjectMapper());
    }

    @Test
    void prefersComposeServiceLabelOverName() {
        Container named = container("named-id", "/caddy", Map.of());
        Container labeled = container("labeled-id", "/proxy", Map.of("com.docker.compose.service", "caddy"));

        assertThat(follower("").selectContainer(List.of(named, labeled))).contains(labeled);
    }

    @Test
    void fallsBackToExactNameCaddy() {
        Container web = container("web-id", "/web", Map.of());
        Container caddy = container("caddy-id", "/caddy", Map.of());

        assertThat(follower("").selectContainer(List.of(web, caddy))).contains(caddy);
    }

    @Test
    void matchesComposeStyleCaddyNames() {
        Container dashed = container("dash-id", "/foo-caddy-1", Map.of());
        Container underscored = container("under-id", "/foo_caddy_1", Map.of());

        assertThat(follower("").selectContainer(List.of(dashed))).contains(dashed);
        assertThat(follower("").selectContainer(List.of(underscored))).contains(underscored);
    }

    @Test
    void explicitConfiguredNameOrIdWins() {
        Container labeled = container("labeled-id", "/caddy", Map.of("com.docker.compose.service", "caddy"));
        Container named = container("abc123def456", "/my-proxy", Map.of());

        assertThat(follower("my-proxy").selectContainer(List.of(labeled, named))).contains(named);
        assertThat(follower("abc123").selectContainer(List.of(labeled, named))).contains(named);
    }

    @Test
    void noMatchReturnsEmpty() {
        Container web = container("web-id", "/web", Map.of("com.docker.compose.service", "web"));

        assertThat(follower("").selectContainer(List.of(web))).isEmpty();
        assertThat(follower("missing").selectContainer(List.of(web))).isEmpty();
        assertThat(follower("").selectContainer(List.of())).isEmpty();
    }

    @Test
    void handleLineIngestsParsedAccessEvent() {
        given(resolve.execute("app.example.com"))
                .willReturn(new ResolveTrafficTarget.ResolvedTarget("lab", "web", "app.example.com"));
        String line = """
                {"request":{"host":"app.example.com"},"status":200,"size":1234,"duration":0.025}
                """;
        CaddyAccessLogFollower follower = follower("");
        follower.start();
        try {
            follower.handleLine(line);
            verify(ingestor).ingestRaw("lab", "web", "app.example.com", 200, 1234L, 25L);
        } finally {
            follower.stop();
        }
    }

    @Test
    void handleLineAfterStopDoesNotIngest() {
        given(resolve.execute("app.example.com"))
                .willReturn(new ResolveTrafficTarget.ResolvedTarget("lab", "web", "app.example.com"));
        String line = """
                {"request":{"host":"app.example.com"},"status":200,"size":1234,"duration":0.025}
                """;
        CaddyAccessLogFollower follower = follower("");
        follower.start();
        try {
            follower.handleLine(line);
            verify(ingestor).ingestRaw("lab", "web", "app.example.com", 200, 1234L, 25L);

            follower.stop();
            follower.handleLine(line);
            verify(ingestor, times(1)).ingestRaw("lab", "web", "app.example.com", 200, 1234L, 25L);
        } finally {
            follower.stop();
        }
    }

    @Test
    void handleLineSwallowsIngestErrorsAndSkipsUnparsedLines() {
        given(resolve.execute("app.example.com"))
                .willReturn(new ResolveTrafficTarget.ResolvedTarget("lab", "web", "app.example.com"));
        doThrow(new RuntimeException("ingest failed"))
                .when(ingestor)
                .ingestRaw(anyString(), anyString(), anyString(), anyInt(), anyLong(), anyLong());
        CaddyAccessLogFollower follower = follower("");
        follower.start();
        try {
            assertDoesNotThrow(() -> follower.handleLine(
                    "{\"request\":{\"host\":\"app.example.com\"},\"status\":200,\"size\":1,\"duration\":0.01}"));
            assertDoesNotThrow(() -> follower.handleLine("INFO serving"));
            verify(ingestor, times(1)).ingestRaw("lab", "web", "app.example.com", 200, 1L, 10L);
        } finally {
            follower.stop();
        }
    }

    @Test
    void enabledByDefaultWhenIngestPropertyIsMissing() {
        assertThat(CaddyAccessLogFollower.class.getAnnotation(Component.class)).isNotNull();
        ConditionalOnProperty condition = CaddyAccessLogFollower.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(condition).isNotNull();
        assertThat(condition.name()).containsExactly("nexus.traffic.ingest-enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isTrue();
    }

    private CaddyAccessLogFollower follower(String configuredContainer) {
        NexusProperties properties = new NexusProperties();
        properties.getTraffic().setCaddyContainer(configuredContainer);
        return new CaddyAccessLogFollower(
                dockerClient, logProvider, parser, resolve, ingestor, properties);
    }

    private static Container container(String id, String name, Map<String, String> labels) {
        Container container = mock(Container.class);
        lenient().when(container.getId()).thenReturn(id);
        lenient().when(container.getNames()).thenReturn(new String[]{name});
        lenient().when(container.getLabels()).thenReturn(labels);
        return container;
    }
}
