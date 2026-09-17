package com.ivan.nexus.application.activity;

import com.ivan.nexus.domain.activity.Activity;
import com.ivan.nexus.infrastructure.persistence.activity.ActivityEventEntity;
import com.ivan.nexus.infrastructure.persistence.activity.ActivityEventJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetActivityTimeline {
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final ActivityEventJpaRepository events;

    public GetActivityTimeline(ActivityEventJpaRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Activity> execute(int limit) {
        int clamped = clampLimit(limit);
        return events.findAllByOrderByCreatedAtDesc(PageRequest.of(0, clamped)).stream()
                .map(ActivityEventEntity::toDomain)
                .toList();
    }

    static int clampLimit(int limit) {
        if (limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
