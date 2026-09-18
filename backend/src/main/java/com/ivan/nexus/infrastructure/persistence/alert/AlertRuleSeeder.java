package com.ivan.nexus.infrastructure.persistence.alert;

import com.ivan.nexus.domain.alert.AlertType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
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
        Set<AlertType> existing = new HashSet<>();
        for (AlertRuleEntity rule : rules.findAll()) {
            if (rule.getProjectId() == null) {
                existing.add(rule.getType());
            }
        }
        int added = 0;
        for (AlertType type : AlertType.values()) {
            if (existing.contains(type)) {
                continue;
            }
            rules.save(new AlertRuleEntity(UUID.randomUUID(), null, type, new HashMap<>(), true));
            added++;
        }
        if (added > 0) {
            log.info("Seeded {} global alert rules", added);
        }
    }
}
