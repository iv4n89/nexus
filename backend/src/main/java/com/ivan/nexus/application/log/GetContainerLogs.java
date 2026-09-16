package com.ivan.nexus.application.log;

import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetContainerLogs {
    static final int DEFAULT_TAIL = 200;
    static final int MAX_TAIL = 2000;

    private final ContainerInventory inventory;
    private final LogProvider logProvider;

    public GetContainerLogs(ContainerInventory inventory, LogProvider logProvider) {
        this.inventory = inventory;
        this.logProvider = logProvider;
    }

    public List<String> execute(String containerId, LogQuery query) {
        if (inventory.findById(containerId).isEmpty()) {
            throw new DomainException(NexusErrorCode.CONTAINER_NOT_FOUND, "Container not found");
        }
        return logProvider.fetch(
                containerId,
                clampTail(query.tail()),
                query.since(),
                query.until(),
                query.timestamps());
    }

    static int clampTail(int tail) {
        if (tail < 1) {
            return DEFAULT_TAIL;
        }
        return Math.min(tail, MAX_TAIL);
    }

    public record LogQuery(int tail, Integer since, Integer until, boolean timestamps) {
    }
}
