package com.ivan.nexus.interfaces.container;

import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/containers")
public class ContainerController {
    private final ContainerInventory inventory;

    public ContainerController(ContainerInventory inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    public List<ContainerResponse> list() {
        return inventory.listAll().stream().map(ContainerResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ContainerResponse get(@PathVariable String id) {
        return inventory.findById(id)
                .map(ContainerResponse::from)
                .orElseThrow(() -> new DomainException(NexusErrorCode.CONTAINER_NOT_FOUND, "Container not found"));
    }

    public record ContainerResponse(
            String id,
            String name,
            String image,
            String status,
            String state,
            String health,
            Instant created,
            Map<String, String> labels,
            List<PortMappingResponse> ports,
            int restartCount,
            Instant startedAt) {
        static ContainerResponse from(ContainerSnapshot snapshot) {
            return new ContainerResponse(
                    snapshot.id(),
                    snapshot.name(),
                    snapshot.image(),
                    snapshot.status(),
                    snapshot.state(),
                    snapshot.health(),
                    snapshot.created(),
                    snapshot.labels(),
                    snapshot.ports().stream()
                            .map(port -> new PortMappingResponse(port.publicPort(), port.privatePort()))
                            .toList(),
                    snapshot.restartCount(),
                    snapshot.startedAt());
        }
    }

    public record PortMappingResponse(Integer publicPort, int privatePort) {
    }
}
