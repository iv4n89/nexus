package com.ivan.nexus.infrastructure.persistence.alert;

import com.ivan.nexus.domain.alert.AlertType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.UUID;

@Component
public class AlertRuleSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AlertRuleSeeder.class);

    private final AlertRuleJpaRepository rules;

    public AlertRuleSeeder(AlertRuleJpaRepository rules) {
        this.rules = rules;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (rules.count() > 0) {
            return;
        }
        for (AlertType type : AlertType.values()) {
            rules.save(new AlertRuleEntity(UUID.randomUUID(), null, type, new HashMap<>(), true));
        }
        log.info("Seeded {} default global alert rules", AlertType.values().length);
    }
}
