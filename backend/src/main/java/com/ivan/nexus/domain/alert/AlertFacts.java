package com.ivan.nexus.domain.alert;

import java.time.Instant;
import java.util.List;

public record AlertFacts(
        Instant now,
        List<ContainerAlertState> previous,
        List<ContainerAlertState> current,
        double diskPercent,
        int diskThreshold,
        List<ErrorRateState> errorRates,
        List<HttpHealthState> httpHealthChecks) {
    public AlertFacts {
        previous = previous == null ? List.of() : List.copyOf(previous);
        current = current == null ? List.of() : List.copyOf(current);
        errorRates = errorRates == null ? List.of() : List.copyOf(errorRates);
        httpHealthChecks = httpHealthChecks == null ? List.of() : List.copyOf(httpHealthChecks);
    }
}
