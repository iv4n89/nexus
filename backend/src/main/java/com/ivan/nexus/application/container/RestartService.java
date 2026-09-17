package com.ivan.nexus.application.container;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.project.ProjectGrouping;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class RestartService {
    private final ContainerRuntime runtime;
    private final ContainerInventory inventory;
    private final RecordAudit recordAudit;
    private final UserDirectory users;

    public RestartService(
            ContainerRuntime runtime,
            ContainerInventory inventory,
            RecordAudit recordAudit,
            UserDirectory users) {
        this.runtime = runtime;
        this.inventory = inventory;
        this.recordAudit = recordAudit;
        this.users = users;
    }

    public void execute(String containerId, String username) {
        ContainerSnapshot snapshot = inventory.findById(containerId)
                .orElseThrow(() -> new DomainException(NexusErrorCode.CONTAINER_NOT_FOUND, "Container not found"));
        runtime.restart(containerId);
        UUID userId = users.findIdByUsername(username).orElseThrow();
        recordAudit.execute(
                userId,
                AuditAction.SERVICE_RESTART,
                ProjectGrouping.projectId(snapshot.name(), snapshot.labels()),
                ProjectGrouping.serviceId(snapshot.name(), snapshot.labels()),
                null,
                Map.of("containerId", containerId));
    }
}
