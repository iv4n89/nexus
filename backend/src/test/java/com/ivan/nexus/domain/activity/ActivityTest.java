package com.ivan.nexus.domain.activity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityTest {

    @Test
    void metadataIsAnInsertionOrderedUnmodifiableDefensiveCopyThatPermitsNulls() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("first", 1);
        source.put("nullable", null);
        source.put(null, "null-key");

        Activity activity = activity(source);
        source.put("first", 2);
        source.put("later", 3);

        assertThat(activity.metadata())
                .containsExactly(
                        Map.entry("first", 1),
                        new java.util.AbstractMap.SimpleImmutableEntry<>("nullable", null),
                        new java.util.AbstractMap.SimpleImmutableEntry<>(null, "null-key"));
        assertThatThrownBy(() -> activity.metadata().put("mutated", true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(activity.metadata()).doesNotContainKey("later");
    }

    @Test
    void nullMetadataBecomesAnEmptyUnmodifiableMap() {
        Activity activity = activity(null);

        assertThat(activity.metadata()).isEmpty();
        assertThatThrownBy(() -> activity.metadata().put("mutated", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static Activity activity(Map<String, Object> metadata) {
        return new Activity(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                Instant.parse("2026-09-18T00:00:00Z"),
                ActivityType.DEPLOYMENT_STARTED,
                "lab",
                "api",
                "started",
                metadata);
    }
}
