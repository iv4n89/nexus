package com.ivan.nexus.application.alert;

import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class GetAlerts {
    static final int MAX_RESULTS = 200;
    private static final List<AlertStatus> OPEN = List.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED);

    private final AlertStore alerts;

    public GetAlerts(AlertStore alerts) {
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public List<Alert> execute(Collection<AlertStatus> statuses) {
        Collection<AlertStatus> filter = (statuses == null || statuses.isEmpty()) ? OPEN : statuses;
        return alerts.latest(filter, MAX_RESULTS);
    }
}
