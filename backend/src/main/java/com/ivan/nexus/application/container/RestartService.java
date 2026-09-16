package com.ivan.nexus.application.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.RestartContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.project.ProjectGrouping;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.user.UserJpaRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class RestartService {
    private final DockerClient dockerClient;
    private final ContainerInventory inventory;
    private final RecordAudit recordAudit;
    private final UserJpaRepository users;

    public RestartService(
            DockerClient dockerClient,
            ContainerInventory inventory,
            RecordAudit recordAudit,
            UserJpaRepository users) {
        this.dockerClient = dockerClient;
        this.inventory = inventory;
        this.recordAudit = recordAudit;
        this.users = users;
    }

    public void execute(String containerId, String username) {
        ContainerSnapshot snapshot = inventory.findById(containerId)
                .orElseThrow(() -> new DomainException(NexusErrorCode.CONTAINER_NOT_FOUND, "Container not found"));
        try {
            run(dockerClient.restartContainerCmd(containerId));
        } catch (NotFoundException ex) {
            throw new DomainException(NexusErrorCode.CONTAINER_NOT_FOUND, "Container not found");
        }
        UUID userId = users.findByUsername(username).orElseThrow().getId();
        recordAudit.execute(
                userId,
                AuditAction.SERVICE_RESTART,
                ProjectGrouping.projectId(snapshot.name(), snapshot.labels()),
                ProjectGrouping.serviceId(snapshot.name(), snapshot.labels()),
                null,
                Map.of("containerId", containerId));
    }

    private static void run(RestartContainerCmd cmd) {
        cmd.exec
                ();
    }
}
