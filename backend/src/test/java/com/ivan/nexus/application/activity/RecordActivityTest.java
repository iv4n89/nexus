package com.ivan.nexus.application.activity;

import com.ivan.nexus.domain.activity.Activity;
import com.ivan.nexus.domain.activity.ActivityType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class RecordActivityTest {

    @Test
    void persistsActivityBeforePublishingIt() {
        ActivityStore store = mock(ActivityStore.class);
        ActivityPublisher publisher = mock(ActivityPublisher.class);

        new RecordActivity(store, publisher).execute(
                ActivityType.DEPLOYMENT_STARTED,
                "lab",
                "api",
                "deployment started",
                Map.of("deploymentId", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        ArgumentCaptor<Activity> activity = ArgumentCaptor.forClass(Activity.class);
        var ordered = inOrder(store, publisher);
        ordered.verify(store).append(activity.capture());
        ordered.verify(publisher).publish(activity.getValue());

        Activity recorded = activity.getValue();
        assertThat(recorded.id()).isNotNull();
        assertThat(recorded.createdAt()).isNotNull();
        assertThat(recorded.type()).isEqualTo(ActivityType.DEPLOYMENT_STARTED);
        assertThat(recorded.projectId()).isEqualTo("lab");
        assertThat(recorded.serviceId()).isEqualTo("api");
        assertThat(recorded.message()).isEqualTo("deployment started");
        assertThat(recorded.metadata())
                .containsEntry("deploymentId", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    @Test
    void replacesNullMetadataWithAnEmptyMap() {
        ActivityStore store = mock(ActivityStore.class);
        ActivityPublisher publisher = mock(ActivityPublisher.class);

        new RecordActivity(store, publisher).execute(
                ActivityType.DEPLOYMENT_STARTED, "lab", "api", "deployment started", null);

        ArgumentCaptor<Activity> activity = ArgumentCaptor.forClass(Activity.class);
        verify(store).append(activity.capture());
        assertThat(activity.getValue().metadata()).isEmpty();
        verify(publisher).publish(activity.getValue());
    }

    @Test
    void copiesMetadataBeforePersistingAndPublishing() {
        ActivityStore store = mock(ActivityStore.class);
        ActivityPublisher publisher = mock(ActivityPublisher.class);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("deploymentId", "original");

        new RecordActivity(store, publisher).execute(
                ActivityType.DEPLOYMENT_STARTED, "lab", "api", "deployment started", metadata);
        metadata.put("deploymentId", "changed");

        ArgumentCaptor<Activity> activity = ArgumentCaptor.forClass(Activity.class);
        verify(store).append(activity.capture());
        assertThat(activity.getValue().metadata()).containsEntry("deploymentId", "original");
        verify(publisher).publish(activity.getValue());
        verifyNoMoreInteractions(store, publisher);
    }
}
