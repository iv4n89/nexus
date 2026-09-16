package com.ivan.nexus.application.project;

import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.project.ProjectGrouping;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetProjectServices {
    private final DiscoverProjects discoverProjects;

    public GetProjectServices(DiscoverProjects discoverProjects) {
        this.discoverProjects = discoverProjects;
    }

    public List<ServiceView> execute(String projectId) {
        List<ContainerSnapshot> containers = discoverProjects.groupByProject().get(projectId);
        if (containers == null || containers.isEmpty()) {
            throw new DomainException(NexusErrorCode.PROJECT_NOT_FOUND, "Project not found");
        }
        return containers.stream().map(GetProjectServices::toService).toList();
    }

    public static ServiceView toService(ContainerSnapshot snapshot) {
        String id = ProjectGrouping.serviceId(snapshot.name(), snapshot.labels());
        return new ServiceView(id, id, snapshot.state(), snapshot.status(), snapshot.health());
    }

    public record ServiceView(String id, String name, String state, String status, String health) {
    }
}
