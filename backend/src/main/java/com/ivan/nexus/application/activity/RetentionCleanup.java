package com.ivan.nexus.application.activity;

import com.ivan.nexus.application.log.FingerprintStore;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import com.ivan.nexus.infrastructure.persistence.activity.ActivityEventJpaRepository;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEventJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class RetentionCleanup {
    private final ActivityEventJpaRepository activityEvents;
    private final DeploymentEventJpaRepository deploymentEvents;
    private final FingerprintStore fingerprints;
    private final NexusProperties properties;
    private final Clock clock;

    @Autowired
    public RetentionCleanup(
            ActivityEventJpaRepository activityEvents,
            DeploymentEventJpaRepository deploymentEvents,
            FingerprintStore fingerprints,
            NexusProperties properties) {
        this(activityEvents, deploymentEvents, fingerprints, properties, Clock.systemUTC());
    }

    RetentionCleanup(
            ActivityEventJpaRepository activityEvents,
            DeploymentEventJpaRepository deploymentEvents,
            FingerprintStore fingerprints,
            NexusProperties properties,
            Clock clock) {
        this.activityEvents = activityEvents;
        this.deploymentEvents = deploymentEvents;
        this.fingerprints = fingerprints;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${nexus.retention.cron:0 0 3 * * *}")
    @Transactional
    public void execute() {
        Instant now = clock.instant();
        NexusProperties.Retention retention = properties.getRetention();
        activityEvents.deleteByCreatedAtBefore(cutoff(now, retention.getActivityDays()));
        deploymentEvents.deleteByCreatedAtBefore(cutoff(now, retention.getDeploymentEventsDays()));
        fingerprints.deleteByLastSeenBefore(cutoff(now, retention.getFingerprintDays()));
    }

    static Instant cutoff(Instant now, int days) {
        return now.minus(Duration.ofDays(days));
    }
}
