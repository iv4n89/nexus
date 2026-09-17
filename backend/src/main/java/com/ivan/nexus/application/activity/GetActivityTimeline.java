package com.ivan.nexus.application.activity;

import com.ivan.nexus.domain.activity.Activity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetActivityTimeline {
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final ActivityStore store;

    public GetActivityTimeline(ActivityStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public List<Activity> execute(int limit) {
        int clamped = clampLimit(limit);
        return store.latest(clamped);
    }

    static int clampLimit(int limit) {
        if (limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
