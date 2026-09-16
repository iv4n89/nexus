package com.ivan.nexus.application.metrics;

import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.domain.metrics.ContainerMetrics;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;

@Service
public class GetContainerMetrics {
    private final ContainerInventory inventory;
    private final ContainerStatsProvider statsProvider;

    public GetContainerMetrics(ContainerInventory inventory, ContainerStatsProvider statsProvider) {
        this.inventory = inventory;
        this.statsProvider = statsProvider;
    }

    public ContainerMetrics execute(String containerId) {
        if (inventory.findById(containerId).isEmpty()) {
            throw new DomainException(NexusErrorCode.CONTAINER_NOT_FOUND, "Container not found");
        }
        return statsProvider.stats(containerId);
    }
}
