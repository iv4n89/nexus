package com.ivan.nexus.application.activity;

import com.ivan.nexus.application.alert.AlertStore;
import com.ivan.nexus.application.project.GetProject;
import com.ivan.nexus.domain.activity.Activity;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class GetIncidentTimeline {
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;
    private static final List<AlertStatus> ALERT_STATUSES =
            List.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED, AlertStatus.RESOLVED);

    private final GetProject getProject;
    private final ActivityStore activities;
    private final AlertStore alerts;

    public GetIncidentTimeline(GetProject getProject, ActivityStore activities, AlertStore alerts) {
        this.getProject = getProject;
        this.activities = activities;
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public List<Item> execute(String projectId, int limit) {
        getProject.execute(projectId);
        int clamped = clampLimit(limit);
        int fetchSize = Math.min(MAX_LIMIT, Math.max(clamped * 2, clamped));

        List<Item> merged = new ArrayList<>();
        for (Activity activity : activities.latest(fetchSize)) {
            if (projectId.equals(activity.projectId())) {
                merged.add(Item.fromActivity(activity));
            }
        }
        for (Alert alert : alerts.latest(ALERT_STATUSES)) {
            if (projectId.equals(alert.projectId())) {
                merged.add(Item.fromAlert(alert));
            }
        }

        return merged.stream()
                .sorted(Comparator.comparing(Item::at).reversed())
                .limit(clamped)
                .toList();
    }

    static int clampLimit(int limit) {
        if (limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    public record Item(
            Instant at,
            String kind,
            String source,
            String message,
            String serviceId,
            UUID refId) {

        static Item fromActivity(Activity activity) {
            return new Item(
                    activity.createdAt(),
                    activity.type().name(),
                    "ACTIVITY",
                    activity.message(),
                    activity.serviceId(),
                    activity.id());
        }

        static Item fromAlert(Alert alert) {
            return new Item(
                    alert.openedAt(),
                    alert.type().name(),
                    "ALERT",
                    alert.message(),
                    alert.serviceId(),
                    alert.id());
        }
    }
}
