package com.ivan.nexus.application.activity;

import com.ivan.nexus.domain.activity.Activity;
import com.ivan.nexus.domain.activity.ActivityType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetActivityTimelineTest {

    @Test
    void returnsNewestFirstUpToRequestedLimit() {
        ActivityStore store = mock(ActivityStore.class);
        Activity newer = activity("newer", Instant.parse("2026-01-01T01:00:00Z"));
        when(store.latest(1)).thenReturn(List.of(newer));

        List<Activity> result = new GetActivityTimeline(store).execute(1);

        assertThat(result).containsExactly(newer);
        verify(store).latest(1);
    }

    @Test
    void clampsLimitToDefaultAndMaximum() {
        assertThat(GetActivityTimeline.clampLimit(0)).isEqualTo(50);
        assertThat(GetActivityTimeline.clampLimit(-3)).isEqualTo(50);
        assertThat(GetActivityTimeline.clampLimit(1)).isEqualTo(1);
        assertThat(GetActivityTimeline.clampLimit(50)).isEqualTo(50);
        assertThat(GetActivityTimeline.clampLimit(200)).isEqualTo(200);
        assertThat(GetActivityTimeline.clampLimit(201)).isEqualTo(200);
    }

    @Test
    void executeUsesClampedPageSizeForOversizedLimit() {
        ActivityStore store = mock(ActivityStore.class);
        when(store.latest(200)).thenReturn(List.of());

        new GetActivityTimeline(store).execute(500);

        verify(store).latest(200);
    }

    private static Activity activity(String message, Instant createdAt) {
        return new Activity(
                UUID.randomUUID(),
                createdAt,
                ActivityType.DEPLOYMENT_STARTED,
                "lab",
                "api",
                message,
                Map.of());
    }
}
