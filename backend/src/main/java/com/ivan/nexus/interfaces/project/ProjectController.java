package com.ivan.nexus.interfaces.project;

import com.ivan.nexus.application.github.GetProjectGitHubStatus;
import com.ivan.nexus.application.github.ProjectGitHubLink;
import com.ivan.nexus.application.github.ProjectGitHubStatusView;
import com.ivan.nexus.application.github.UpdateProjectGitHubSettings;
import com.ivan.nexus.application.log.GetRecentErrors;
import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.application.project.GetProject;
import com.ivan.nexus.application.project.GetProjectServices;
import com.ivan.nexus.domain.project.Project;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final DiscoverProjects discoverProjects;
    private final GetProject getProject;
    private final GetProjectServices getProjectServices;
    private final GetRecentErrors getRecentErrors;
    private final GetProjectGitHubStatus getProjectGitHubStatus;
    private final UpdateProjectGitHubSettings updateProjectGitHubSettings;

    public ProjectController(
            DiscoverProjects discoverProjects,
            GetProject getProject,
            GetProjectServices getProjectServices,
            GetRecentErrors getRecentErrors,
            GetProjectGitHubStatus getProjectGitHubStatus,
            UpdateProjectGitHubSettings updateProjectGitHubSettings) {
        this.discoverProjects = discoverProjects;
        this.getProject = getProject;
        this.getProjectServices = getProjectServices;
        this.getRecentErrors = getRecentErrors;
        this.getProjectGitHubStatus = getProjectGitHubStatus;
        this.updateProjectGitHubSettings = updateProjectGitHubSettings;
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
                result.containers().stream().map(GetProjectServices::toService).toList(),
                getRecentErrors.execute(id));
    }

    @GetMapping("/{id}/github-status")
    public GitHubStatusResponse githubStatus(@PathVariable String id) {
        return GitHubStatusResponse.from(getProjectGitHubStatus.execute(id));
    }

    @PatchMapping("/{id}/github")
    public GitHubLinkResponse updateGitHub(
            @PathVariable String id,
            @RequestBody UpdateGitHubRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        if (request.autodeployEnabled() == null) {
            throw new DomainException(
                    NexusErrorCode.OPERATION_NOT_ALLOWED,
                    "autodeployEnabled is required");
        }
        ProjectGitHubLink link = updateProjectGitHubSettings.execute(
                id,
                request.autodeployEnabled(),
                authentication.getName(),
                ClientIp.resolve(httpRequest));
        return GitHubLinkResponse.from(link);
    }

    @GetMapping("/{id}/errors")
    public List<RecentErrorResponse> errors(
            @PathVariable String id, @RequestParam(required = false) String serviceId) {
        getProject.execute(id);
        return getRecentErrors.execute(id, serviceId).stream().map(RecentErrorResponse::from).toList();
    }

    @GetMapping("/{id}/services")
    public List<ProjectServiceResponse> services(@PathVariable String id) {
        return getProjectServices.execute(id).stream().map(ProjectServiceResponse::from).toList();
    }

    public record UpdateGitHubRequest(Boolean autodeployEnabled) {
    }

    public record GitHubLinkResponse(
            String projectId,
            String owner,
            String repo,
            String branch,
            boolean autodeployEnabled) {
        static GitHubLinkResponse from(ProjectGitHubLink link) {
            return new GitHubLinkResponse(
                    link.projectId(),
                    link.owner(),
                    link.repo(),
                    link.branch(),
                    link.autodeployEnabled());
        }
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
            List<ProjectServiceResponse> services,
            List<RecentErrorResponse> recentErrors) {
        static ProjectDetailResponse from(
                Project project,
                List<GetProjectServices.ServiceView> services,
                List<GetRecentErrors.RecentError> recentErrors) {
            return new ProjectDetailResponse(
                    project.id(),
                    project.name(),
                    project.status(),
                    project.runningCount(),
                    project.totalCount(),
                    project.deployable(),
                    services.stream().map(ProjectServiceResponse::from).toList(),
                    recentErrors.stream().map(RecentErrorResponse::from).toList());
        }
    }

    public record RecentErrorResponse(
            String serviceId, String sampleMessage, long count, Instant firstSeen, Instant lastSeen) {
        static RecentErrorResponse from(GetRecentErrors.RecentError error) {
            return new RecentErrorResponse(
                    error.serviceId(),
                    error.sampleMessage(),
                    error.count(),
                    error.firstSeen(),
                    error.lastSeen());
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

    public record GitHubStatusResponse(
            String projectId,
            String owner,
            String repo,
            String branch,
            String remoteHeadSha,
            String deployedCommitSha,
            boolean upToDate,
            boolean autodeployEnabled) {
        static GitHubStatusResponse from(ProjectGitHubStatusView view) {
            return new GitHubStatusResponse(
                    view.projectId(),
                    view.owner(),
                    view.repo(),
                    view.branch(),
                    view.remoteHeadSha(),
                    view.deployedCommitSha(),
                    view.upToDate(),
                    view.autodeployEnabled());
        }
    }
}
