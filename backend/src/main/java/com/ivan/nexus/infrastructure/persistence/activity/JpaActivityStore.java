package com.ivan.nexus.infrastructure.persistence.activity;

import com.ivan.nexus.application.activity.ActivityStore;
import com.ivan.nexus.domain.activity.Activity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class JpaActivityStore implements ActivityStore {
    private final ActivityEventJpaRepository repository;

    public JpaActivityStore(ActivityEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(Activity activity) {
        repository.save(toEntity(activity));
    }

    @Override
    public List<Activity> latest(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(JpaActivityStore::toDomain)
                .toList();
    }

    @Override
    public long deleteCreatedBefore(Instant cutoff) {
        return repository.deleteByCreatedAtBefore(cutoff);
    }

    private static ActivityEventEntity toEntity(Activity activity) {
        return new ActivityEventEntity(
                activity.id(),
                activity.createdAt(),
                activity.type(),
                activity.projectId(),
                activity.serviceId(),
                activity.message(),
                activity.metadata());
    }

    private static Activity toDomain(ActivityEventEntity entity) {
        return new Activity(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getType(),
                entity.getProjectId(),
                entity.getServiceId(),
                entity.getMessage(),
                entity.getMetadata());
    }
}
