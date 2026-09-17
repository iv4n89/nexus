package com.ivan.nexus.infrastructure.sse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ivan.nexus.domain.activity.Activity;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.interfaces.activity.ActivityResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class ActivityHubTest {

    private ObjectMapper objectMapper;
    private ActivityHub hub;

    @BeforeEach
    void setUp() {
        objectMapper = spy(JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
        hub = new ActivityHub(objectMapper);
    }

    @AfterEach
    void tearDown() {
        hub.shutdown();
    }

    @Test
    void publishesActivityJsonWithHttpFieldNames() throws Exception {
        Activity activity = new Activity(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                Instant.parse("2026-01-01T00:42:12Z"),
                ActivityType.DEPLOYMENT_STARTED,
                "lab",
                "api",
                "deployment started",
                Map.of("deploymentId", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

        hub.publish(activity);

        verify(objectMapper).writeValueAsString(activity);
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(activity));
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "createdAt", "type", "projectId", "serviceId", "message", "metadata");
        assertThat(json).isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(ActivityResponse.from(activity))));
        assertThat(json.get("id").asText()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(json.get("createdAt").asText()).isEqualTo("2026-01-01T00:42:12Z");
        assertThat(json.get("type").asText()).isEqualTo("DEPLOYMENT_STARTED");
        assertThat(json.get("projectId").asText()).isEqualTo("lab");
        assertThat(json.get("serviceId").asText()).isEqualTo("api");
        assertThat(json.get("message").asText()).isEqualTo("deployment started");
        assertThat(json.get("metadata").get("deploymentId").asText())
                .isEqualTo("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    }
}
