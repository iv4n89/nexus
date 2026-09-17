package com.ivan.nexus.application.activity;

import com.ivan.nexus.domain.activity.Activity;

import java.time.Instant;
import java.util.List;

public interface ActivityStore {
    void append(Activity activity);

    List<Activity> latest(int limit);

    long deleteCreatedBefore(Instant cutoff);
}
