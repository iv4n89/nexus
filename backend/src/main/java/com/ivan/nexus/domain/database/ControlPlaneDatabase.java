package com.ivan.nexus.domain.database;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;

public final class ControlPlaneDatabase {
    public static final String PROJECT_ID = "nexus";

    private ControlPlaneDatabase() {}

    public static boolean isControlPlane(String projectId) {
        return PROJECT_ID.equals(projectId);
    }

    public static void requireAdminForDataAccess(String projectId, String role) {
        if (isControlPlane(projectId) && !"ADMIN".equals(role)) {
            throw new DomainException(
                    NexusErrorCode.FORBIDDEN,
                    "Control-plane database is admin-only");
        }
    }
}
