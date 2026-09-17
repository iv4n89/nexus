package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.domain.alert.AlertEvaluator;
import com.ivan.nexus.domain.alert.AlertEvaluation;
import com.ivan.nexus.domain.alert.AlertFacts;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.alert.ContainerAlertState;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.project.Project;
import com.ivan.nexus.domain.project.ProjectGrouping;
import com.ivan.nexus.infrastructure.persistence.alert.AlertRuleEntity;
import com.ivan.nexus.infrastructure.persistence.alert.AlertRuleJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "nexus.alerts.enabled", havingValue = "true")
public class EvaluateAlerts {
    private static final Logger log = LoggerFactory.getLogger(EvaluateAlerts.class);

    private final DiscoverProjects discoverProjects;
    private final ManifestCatalog manifests;
    private final AlertRuleJpaRepository rules;
    private final PersistAlertEvaluation persistAlertEvaluation;
    private final AlertFactCollector factCollector;
    private final ContainerLifecycleNotifier lifecycleNotifier;
    private final AlertEvaluator evaluator = new AlertEvaluator();
    private final ConcurrentHashMap<String, ContainerSnapshot> previousSnapshots = new ConcurrentHashMap<>();
    private volatile boolean primed;

    public EvaluateAlerts(
            DiscoverProjects discoverProjects,
            ManifestCatalog manifests,
            AlertRuleJpaRepository rules,
            PersistAlertEvaluation persistAlertEvaluation,
            AlertFactCollector factCollector,
            ContainerLifecycleNotifier lifecycleNotifier) {
        this.discoverProjects = discoverProjects;
        this.manifests = manifests;
        this.rules = rules;
        this.persistAlertEvaluation = persistAlertEvaluation;
        this.factCollector = factCollector;
        this.lifecycleNotifier = lifecycleNotifier;
    }

    @Scheduled(fixedDelayString = "${nexus.alerts.interval-ms:30000}")
    public void execute() {
        Instant now = Instant.now();
        List<AlertRuleEntity> enabledRules = rules.findByEnabledTrue();
        Map<String, List<ContainerSnapshot>> grouped = discoverProjects.groupByProject();
        Map<String, ProjectManifest> manifests = loadManifests();

        List<ContainerAlertState> current = new ArrayList<>();
        List<ContainerAlertState> previous = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            String projectId = entry.getKey();
            int memoryThreshold = AlertFactCollector.memoryThreshold(enabledRules, projectId, manifests.get(projectId));
            for (ContainerSnapshot container : entry.getValue()) {
                String serviceId = serviceId(container);
                current.add(factCollector.toState(container, projectId, serviceId, memoryThreshold));
                ContainerSnapshot prior = previousSnapshots.get(container.id());
                if (prior != null) {
                    previous.add(factCollector.toState(prior, projectId, serviceId, memoryThreshold));
                }
            }
        }

        AlertFacts facts = new AlertFacts(
                now,
                previous,
                current,
                factCollector.diskPercent(),
                AlertFactCollector.diskThreshold(enabledRules),
                factCollector.errorRates(enabledRules, manifests),
                factCollector.httpHealthChecks(enabledRules, manifests));

        AlertEvaluation evaluation = evaluator.evaluate(facts);
        persistAlertEvaluation.persist(evaluation, enabledRules, now);
        lifecycleNotifier.emitContainerActivity(grouped, previousSnapshots, primed);

        previousSnapshots.clear();
        for (List<ContainerSnapshot> containers : grouped.values()) {
            for (ContainerSnapshot container : containers) {
                previousSnapshots.put(container.id(), container);
            }
        }
        primed = true;
    }

    private Map<String, ProjectManifest> loadManifests() {
        Map<String, ProjectManifest> manifests = new HashMap<>();
        for (Project project : discoverProjects.execute()) {
            if (!project.deployable()) {
                continue;
            }
            try {
                manifests.put(project.id(), this.manifests.loadRequired(project.id()).manifest());
            } catch (RuntimeException ex) {
                log.warn("Unable to load manifest for project {}", project.id(), ex);
            }
        }
        return manifests;
    }

    static AlertRuleEntity ruleFor(List<AlertRuleEntity> enabledRules, AlertType type, String projectId) {
        AlertRuleEntity global = null;
        for (AlertRuleEntity rule : enabledRules) {
            if (rule.getType() != type) {
                continue;
            }
            if (projectId != null && projectId.equals(rule.getProjectId())) {
                return rule;
            }
            if (rule.getProjectId() == null) {
                global = rule;
            }
        }
        return global;
    }

    private static String serviceId(ContainerSnapshot container) {
        return ProjectGrouping.serviceId(container.name(), container.labels());
    }
}
