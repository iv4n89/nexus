package com.ivan.nexus.infrastructure.persistence.activity;

import com.ivan.nexus.application.activity.ActivityStore;
import com.ivan.nexus.domain.activity.Activity;
import com.ivan.nexus.domain.activity.ActivityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaActivityStoreTest {
    private ActivityEventJpaRepository repository;
    private ActivityStore store;

    @BeforeEach
    void setUp() {
        repository = mock(ActivityEventJpaRepository.class);
        store = new JpaActivityStore(repository);
    }

    @Test
    void appendMapsDomainActivityToEntityAndCopiesMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("deploymentId", "original");
        Activity activity = activity("started", Instant.parse("2026-09-17T20:00:00Z"), metadata);

        store.append(activity);
        metadata.put("deploymentId", "changed");

        ArgumentCaptor<ActivityEventEntity> captor = ArgumentCaptor.forClass(ActivityEventEntity.class);
        verify(repository).save(captor.capture());
        ActivityEventEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo(activity.id());
        assertThat(entity.getCreatedAt()).isEqualTo(activity.createdAt());
        assertThat(entity.getType()).isEqualTo(activity.type());
        assertThat(entity.getProjectId()).isEqualTo(activity.projectId());
        assertThat(entity.getServiceId()).isEqualTo(activity.serviceId());
        assertThat(entity.getMessage()).isEqualTo(activity.message());
        assertThat(entity.getMetadata()).containsEntry("deploymentId", "original");
    }

    @Test
    void latestDelegatesBoundedNewestFirstQueryAndMapsEntities() {
        Activity newer = activity("newer", Instant.parse("2026-09-17T21:00:00Z"), Map.of("key", "value"));
        Activity older = activity("older", Instant.parse("2026-09-17T20:00:00Z"), Map.of());
        when(repository.findAllByOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(entity(newer), entity(older)));

        assertThat(store.latest(17)).containsExactly(newer, older);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByOrderByCreatedAtDesc(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(17);
    }

    @Test
    void deleteCreatedBeforeDelegates() {
        Instant cutoff = Instant.parse("2026-09-10T03:00:00Z");
        when(repository.deleteByCreatedAtBefore(cutoff)).thenReturn(3L);

        assertThat(store.deleteCreatedBefore(cutoff)).isEqualTo(3L);
        verify(repository).deleteByCreatedAtBefore(cutoff);
    }

    private static Activity activity(String message, Instant createdAt, Map<String, Object> metadata) {
        return new Activity(
                UUID.randomUUID(),
                createdAt,
                ActivityType.DEPLOYMENT_STARTED,
                "lab",
                "api",
                message,
                metadata);
    }

    private static ActivityEventEntity entity(Activity activity) {
        return new ActivityEventEntity(
                activity.id(),
                activity.createdAt(),
                activity.type(),
                activity.projectId(),
                activity.serviceId(),
                activity.message(),
                activity.metadata());
    }
}
