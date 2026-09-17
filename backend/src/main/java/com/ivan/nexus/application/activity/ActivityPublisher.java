package com.ivan.nexus.application.activity;

import com.ivan.nexus.domain.activity.Activity;

public interface ActivityPublisher {
    void publish(Activity activity);
}
