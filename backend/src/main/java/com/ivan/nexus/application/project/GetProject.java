package com.ivan.nexus.application.project;

import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.project.Project;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetProject {
    private final DiscoverProjects discoverProjects;

    public GetProject(DiscoverProjects discoverProjects) {
        this.discoverProjects = discoverProjects;
    }

    public Result execute(String projectId) {
        List<ContainerSnapshot> containers = discoverProjects.groupByProject().get(projectId);
        if (containers == null) {
            throw new DomainException(NexusErrorCode.PROJECT_NOT_FOUND, "Project not found");
        }
        return new Result(discoverProjects.toProject(projectId, containers), List.copyOf(containers));
    }

    public record Result(Project project, List<ContainerSnapshot> containers) {
    }
}
