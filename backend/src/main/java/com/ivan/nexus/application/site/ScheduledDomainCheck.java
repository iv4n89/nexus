package com.ivan.nexus.application.site;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ivan.nexus.domain.site.SiteDomain;

/**
 * Daily DNS/HTTPS certificate status check for all registered domains.
 */
@Service
@ConditionalOnProperty(name = "nexus.caddy.scheduler-enabled", havingValue = "true")
public class ScheduledDomainCheck {
    private static final Logger log = LoggerFactory.getLogger(ScheduledDomainCheck.class);

    private final DomainStore domains;
    private final CheckDomainStatus checkDomainStatus;

    public ScheduledDomainCheck(DomainStore domains, CheckDomainStatus checkDomainStatus) {
        this.domains = domains;
        this.checkDomainStatus = checkDomainStatus;
    }

    @Scheduled(cron = "${nexus.caddy.cron:0 0 5 * * *}")
    public void execute() {
        for (SiteDomain domain : domains.findAll()) {
            try {
                checkDomainStatus.execute(domain);
            } catch (RuntimeException ex) {
                log.warn("Domain status check failed for {}: {}", domain.hostname(), ex.getMessage());
            }
        }
    }
}
