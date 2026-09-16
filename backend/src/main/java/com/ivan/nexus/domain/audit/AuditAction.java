package com.ivan.nexus.domain.audit;

public enum AuditAction {
    LOGIN,
    DEPLOY,
    ROLLBACK,
    SERVICE_RESTART,
    CONFIG_CHANGE,
    ALERT_ACKNOWLEDGE
}
