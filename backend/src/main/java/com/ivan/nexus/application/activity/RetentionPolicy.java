package com.ivan.nexus.application.activity;

import java.time.Duration;

public record RetentionPolicy(
        Duration activity,
        Duration deploymentEvents,
        Duration fingerprints) {
}
