package com.ivan.nexus.application.alert;

import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertKey;
import com.ivan.nexus.domain.alert.AlertStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertStore {
    List<Alert> latest(Collection<AlertStatus> statuses, int limit);

    Optional<Alert> findById(UUID id);

    Optional<Alert> findOpen(AlertKey key);

    void open(Alert alert);

    Optional<Alert> acknowledge(UUID id, Instant at);

    Optional<Alert> resolve(AlertKey key, Instant at);
}
