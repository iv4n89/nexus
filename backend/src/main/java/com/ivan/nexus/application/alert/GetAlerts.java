package com.ivan.nexus.application.alert;

import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventEntity;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class GetAlerts {
    private static final List<AlertStatus> OPEN = List.of(AlertStatus.ACTIVE, AlertStatus.ACKNOWLEDGED);

    private final AlertEventJpaRepository events;

    public GetAlerts(AlertEventJpaRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Alert> execute(Collection<AlertStatus> statuses) {
        Collection<AlertStatus> filter = (statuses == null || statuses.isEmpty()) ? OPEN : statuses;
        return events.findByStatusInOrderByOpenedAtDesc(filter).stream()
                .map(AlertEventEntity::toDomain)
                .toList();
    }
}
