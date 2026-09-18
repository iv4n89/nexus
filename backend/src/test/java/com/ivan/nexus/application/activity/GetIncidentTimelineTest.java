package com.ivan.nexus.application.activity;

import com.ivan.nexus.application.alert.AlertStore;
import com.ivan.nexus.application.project.GetProject;
import com.ivan.nexus.domain.activity.Activity;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.project.Project;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetIncidentTimelineTest {

    @Test
    void mergesProjectActivityAndAlertsNewestFirst() {
        GetProject getProject = mock(GetProject.class);
        ActivityStore activities = mock(ActivityStore.class);
        AlertStore alerts = mock(AlertStore.class);

        when(getProject.execute("lab")).thenReturn(new GetProject.Result(
                new Project("lab", "lab", "HEALTHY", 1, 1, true),
                List.<ContainerSnapshot>of()));
        when(activities.latest(anyInt())).thenReturn(List.of(
                activity("lab", "deploy done", Instant.parse("2026-01-01T01:00:00Z")),
                activity("other", "noise", Instant.parse("2026-01-01T02:00:00Z")),
                activity("lab", "restart", Instant.parse("2026-01-01T00:30:00Z"))));
        when(alerts.latest(any())).thenReturn(List.of(
                alert("lab", "api down", Instant.parse("2026-01-01T00:45:00Z")),
                alert("other", "disk", Instant.parse("2026-01-01T03:00:00Z"))));

        List<GetIncidentTimeline.Item> result =
                new GetIncidentTimeline(getProject, activities, alerts).execute("lab", 10);

        assertThat(result).extracting(GetIncidentTimeline.Item::message)
                .containsExactly("deploy done", "api down", "restart");
        assertThat(result).extracting(GetIncidentTimeline.Item::source)
                .containsExactly("ACTIVITY", "ALERT", "ACTIVITY");
    }

    @Test
    void clampsLimitAndFetchesBoundedActivityWindow() {
        GetProject getProject = mock(GetProject.class);
        ActivityStore activities = mock(ActivityStore.class);
        AlertStore alerts = mock(AlertStore.class);

        when(getProject.execute("lab")).thenReturn(new GetProject.Result(
                new Project("lab", "lab", "DOWN", 0, 0, true),
                List.of()));
        when(activities.latest(anyInt())).thenReturn(List.of());
        when(alerts.latest(any())).thenReturn(List.of());

        new GetIncidentTimeline(getProject, activities, alerts).execute("lab", 500);

        verify(activities).latest(200);
        assertThat(GetIncidentTimeline.clampLimit(0)).isEqualTo(50);
        assertThat(GetIncidentTimeline.clampLimit(201)).isEqualTo(200);
    }

    private static Activity activity(String projectId, String message, Instant at) {
        return new Activity(
                UUID.randomUUID(),
                at,
                ActivityType.DEPLOYMENT_SUCCESS,
                projectId,
                "api",
                message,
                Map.of());
    }

    private static Alert alert(String projectId, String message, Instant at) {
        return new Alert(
                UUID.randomUUID(),
                UUID.randomUUID(),
                projectId,
                "api",
                AlertStatus.ACTIVE,
                message,
                at,
                null,
                null,
                AlertType.CONTAINER_STOPPED);
    }
}
