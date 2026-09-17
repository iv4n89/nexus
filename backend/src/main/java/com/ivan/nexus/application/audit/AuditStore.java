package com.ivan.nexus.application.audit;

import com.ivan.nexus.domain.audit.AuditEvent;

public interface AuditStore {
    void append(AuditEvent event);
}
