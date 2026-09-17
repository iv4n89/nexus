package com.ivan.nexus.domain.alert;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

public record AlertRule(
        UUID id,
        String projectId,
        AlertType type,
        Map<String, Object> thresholds,
        boolean enabled) {

    public AlertRule {
        thresholds = Collections.unmodifiableMap(
                thresholds == null ? new LinkedHashMap<>() : new LinkedHashMap<>(thresholds));
    }

    public OptionalInt threshold(String key) {
        Object value = thresholds.get(key);
        return value instanceof Number number
                ? OptionalInt.of(number.intValue())
                : OptionalInt.empty();
    }
}
