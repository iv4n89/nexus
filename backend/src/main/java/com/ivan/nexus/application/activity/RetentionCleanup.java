package com.ivan.nexus.application.activity;

import com.ivan.nexus.application.deployment.DeploymentEventStore;
import com.ivan.nexus.application.log.FingerprintStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class RetentionCleanup {
    private final ActivityStore activityEvents;
    private final DeploymentEventStore deploymentEvents;
    private final FingerprintStore fingerprints;
    private final RetentionPolicy policy;
    private final Clock clock;

    public RetentionCleanup(
            ActivityStore activityEvents,
            DeploymentEventStore deploymentEvents,
            FingerprintStore fingerprints,
            RetentionPolicy policy,
            Clock clock) {
        this.activityEvents = activityEvents;
        this.deploymentEvents = deploymentEvents;
        this.fingerprints = fingerprints;
        this.policy = policy;
        this.clock = clock;
    }

    @Scheduled(cron = "${nexus.retention.cron:0 0 3 * * *}")
    @Transactional
    public void execute() {
        Instant now = clock.instant();
        activityEvents.deleteCreatedBefore(cutoff(now, policy.activity()));
        deploymentEvents.deleteCreatedBefore(cutoff(now, policy.deploymentEvents()));
        fingerprints.deleteByLastSeenBefore(cutoff(now, policy.fingerprints()));
    }

    static Instant cutoff(Instant now, Duration retention) {
        return now.minus(retention);
    }
}
