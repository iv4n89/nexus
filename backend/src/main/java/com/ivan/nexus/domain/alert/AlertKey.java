package com.ivan.nexus.domain.alert;

public record AlertKey(AlertType type, String projectId, String serviceId) {
}
