package com.ivan.nexus.domain.alert;

public record ErrorRateState(String projectId, String serviceId, int newHits, int threshold) {
}
