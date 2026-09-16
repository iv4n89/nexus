package com.ivan.nexus.application.activity;

import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.infrastructure.persistence.activity.ActivityEventEntity;
import com.ivan.nexus.infrastructure.persistence.activity.ActivityEventJpaRepository;
import com.ivan.nexus.interfaces.activity.ActivityResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetActivityTimelineTest {

    @Test
    void returnsNewestFirstUpToRequestedLimit() {
        ActivityEventJpaRepository events = mock(ActivityEventJpaRepository.class);
        ActivityEventEntity newer = event("newer", Instant.parse("2026-01-01T01:00:00Z"));
        ActivityEventEntity older = event("older", Instant.parse("2026-01-01T00:00:00Z"));
        when(events.findAllByOrderByCreatedAtDesc(any())).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            List<ActivityEventEntity> all = List.of(newer, older);
            return all.subList(0, Math.min(pageable.getPageSize(), all.size()));
        });

        List<ActivityResponse> result = new GetActivityTimeline(events).execute(1);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().message()).isEqualTo("newer");
        assertThat(result.getFirst().type()).isEqualTo(ActivityType.DEPLOYMENT_STARTED);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(events).findAllByOrderByCreatedAtDesc(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
        assertThat(captor.getValue().getPageNumber()).isZero();
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
        ActivityEventJpaRepository events = mock(ActivityEventJpaRepository.class);
        when(events.findAllByOrderByCreatedAtDesc(any())).thenReturn(List.of());

        new GetActivityTimeline(events).execute(500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(events).findAllByOrderByCreatedAtDesc(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(200);
    }

    private static ActivityEventEntity event(String message, Instant createdAt) {
        return new ActivityEventEntity(
                UUID.randomUUID(),
                createdAt,
                ActivityType.DEPLOYMENT_STARTED,
                "lab",
                "api",
                message,
                Map.of());
    }
}
