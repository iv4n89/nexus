package com.ivan.nexus.domain.deployment;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;

public final class DeploymentTransitions {

    private DeploymentTransitions() {
    }

    public static DeploymentStatus next(DeploymentStatus from, DeploymentStatus to) {
        boolean ok = switch (from) {
            case PENDING -> to == DeploymentStatus.RUNNING || to == DeploymentStatus.CANCELLED;
            case RUNNING -> to == DeploymentStatus.SUCCESS
                    || to == DeploymentStatus.FAILED
                    || to == DeploymentStatus.CANCELLED;
            default -> false;
        };
        if (!ok) {
            throw new DomainException(NexusErrorCode.INVALID_TRANSITION, "Invalid deployment transition");
        }
        return to;
    }
}
