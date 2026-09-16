package com.ivan.nexus.application.deployment;

import java.time.Duration;

public interface HealthChecker {
    boolean check(String url, Duration timeout);
}
