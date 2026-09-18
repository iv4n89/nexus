package com.ivan.nexus.domain.audit;

public enum AuditAction {
    LOGIN,
    DEPLOY,
    ROLLBACK,
    SERVICE_RESTART,
    CONFIG_CHANGE,
    ALERT_ACKNOWLEDGE,
    DB_QUERY,
    DB_CELL_EDIT,
    GITHUB_CONNECT,
    GITHUB_DISCONNECT
}
