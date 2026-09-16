package com.ivan.nexus.application.project;

import com.ivan.nexus.domain.container.ContainerSnapshot;

import java.util.List;
import java.util.Optional;

public interface ContainerInventory {
    List<ContainerSnapshot> listAll();

    Optional<ContainerSnapshot> findById(String containerId);

    default Optional<ContainerInspect> inspect(String containerId) {
        return Optional.empty();
    }
}
