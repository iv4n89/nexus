package com.ivan.nexus.application.activity;

import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.infrastructure.persistence.activity.ActivityEventEntity;
import com.ivan.nexus.infrastructure.persistence.activity.ActivityEventJpaRepository;
import com.ivan.nexus.infrastructure.sse.ActivityHub;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordActivityTest {

    @Test
    void persistsEventThenPublishesToHub() {
        ActivityEventJpaRepository events = mock(ActivityEventJpaRepository.class);
        ActivityHub hub = mock(ActivityHub.class);
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new RecordActivity(events, hub).execute(
                ActivityType.DEPLOYMENT_STARTED,
                "lab",
                "api",
                "deployment started",
                Map.of("deploymentId", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        ArgumentCaptor<ActivityEventEntity> captor = ArgumentCaptor.forClass(ActivityEventEntity.class);
        verify(events).save(captor.capture());
        ActivityEventEntity saved = captor.getValue();
        verify(hub).publish(saved);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getType()).isEqualTo(ActivityType.DEPLOYMENT_STARTED);
        assertThat(saved.getProjectId()).isEqualTo("lab");
        assertThat(saved.getServiceId()).isEqualTo("api");
        assertThat(saved.getMessage()).isEqualTo("deployment started");
        assertThat(saved.getMetadata()).containsEntry("deploymentId", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }
}
