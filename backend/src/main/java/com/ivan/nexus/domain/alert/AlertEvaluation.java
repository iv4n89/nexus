package com.ivan.nexus.domain.alert;

import java.util.List;

public record AlertEvaluation(List<AlertFiring> firings, List<AlertKey> resolveKeys) {
    public AlertEvaluation {
        firings = List.copyOf(firings);
        resolveKeys = List.copyOf(resolveKeys);
    }
}
