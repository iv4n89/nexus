package com.ivan.nexus.application.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Stub periodic GitHub sync tick (H4). Enable with {@code nexus.github.sync-enabled=true}.
 */
@Service
@ConditionalOnProperty(name = "nexus.github.sync-enabled", havingValue = "true")
public class ScheduledGitHubSync {
    private static final Logger log = LoggerFactory.getLogger(ScheduledGitHubSync.class);

    @Scheduled(cron = "${nexus.github.sync-cron:0 */15 * * * *}")
    public void execute() {
        log.debug("GitHub sync scheduler tick (stub — no remote calls)");
    }
}
