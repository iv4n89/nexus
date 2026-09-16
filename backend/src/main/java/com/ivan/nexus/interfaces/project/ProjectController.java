package com.ivan.nexus.interfaces.project;

import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.application.project.GetProject;
import com.ivan.nexus.application.project.GetProjectServices;
import com.ivan.nexus.domain.project.Project;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final DiscoverProjects discoverProjects;
    private final GetProject getProject;
    private final GetProjectServices getProjectServices;

    public ProjectController(
            DiscoverProjects discoverProjects,
            GetProject getProject,
            GetProjectServices getProjectServices) {
        this.discoverProjects = discoverProjects;
        this.getProject = getProject;
        this.getProjectServices = getProjectServices;
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return discoverProjects.execute().stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProjectDetailResponse get(@PathVariable String id) {
        GetProject.Result result = getProject.execute(id);
        return ProjectDetailResponse.from(
                result.project(),
                result.containers().stream().map(GetProjectServices::toService).toList());
    }

    @GetMapping("/{id}/services")
    public List<ProjectServiceResponse> services(@PathVariable String id) {
        return getProjectServices.execute(id).stream().map(ProjectServiceResponse::from).toList();
    }

    public record ProjectResponse(
            String id,
            String name,
            String status,
            int runningCount,
            int totalCount,
            boolean deployable) {
        static ProjectResponse from(Project project) {
            return new ProjectResponse(
                    project.id(),
                    project.name(),
                    project.status(),
                    project.runningCount(),
                    project.totalCount(),
                    project.deployable());
        }
    }

    public record ProjectDetailResponse(
            String id,
            String name,
            String status,
            int runningCount,
            int totalCount,
            boolean deployable,
            List<ProjectServiceResponse> services) {
        static ProjectDetailResponse from(Project project, List<GetProjectServices.ServiceView> services) {
            return new ProjectDetailResponse(
                    project.id(),
                    project.name(),
                    project.status(),
                    project.runningCount(),
                    project.totalCount(),
                    project.deployable(),
                    services.stream().map(ProjectServiceResponse::from).toList());
        }
    }

    public record ProjectServiceResponse(
            String id,
            String name,
            String state,
            String status,
            String health) {
        static ProjectServiceResponse from(GetProjectServices.ServiceView service) {
            return new ProjectServiceResponse(
                    service.id(),
                    service.name(),
                    service.state(),
                    service.status(),
                    service.health());
        }
    }
}
