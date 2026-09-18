package com.ivan.nexus.interfaces.github;

import com.ivan.nexus.application.github.ConnectGitHub;
import com.ivan.nexus.application.github.CreateProjectFromRepo;
import com.ivan.nexus.application.github.DisconnectGitHub;
import com.ivan.nexus.application.github.GetGitHubConnection;
import com.ivan.nexus.application.github.ListGitHubBranches;
import com.ivan.nexus.application.github.ListGitHubRepositories;
import com.ivan.nexus.application.github.StartGitHubOAuth;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GitHubController {
    private final GetGitHubConnection getGitHubConnection;
    private final StartGitHubOAuth startGitHubOAuth;
    private final ConnectGitHub connectGitHub;
    private final DisconnectGitHub disconnectGitHub;
    private final CreateProjectFromRepo createProjectFromRepo;
    private final ListGitHubRepositories listGitHubRepositories;
    private final ListGitHubBranches listGitHubBranches;

    public GitHubController(
            GetGitHubConnection getGitHubConnection,
            StartGitHubOAuth startGitHubOAuth,
            ConnectGitHub connectGitHub,
            DisconnectGitHub disconnectGitHub,
            CreateProjectFromRepo createProjectFromRepo,
            ListGitHubRepositories listGitHubRepositories,
            ListGitHubBranches listGitHubBranches) {
        this.getGitHubConnection = getGitHubConnection;
        this.startGitHubOAuth = startGitHubOAuth;
        this.connectGitHub = connectGitHub;
        this.disconnectGitHub = disconnectGitHub;
        this.createProjectFromRepo = createProjectFromRepo;
        this.listGitHubRepositories = listGitHubRepositories;
        this.listGitHubBranches = listGitHubBranches;
    }

    @GetMapping("/api/github/status")
    public GitHubDtos.StatusResponse status() {
        return GitHubDtos.StatusResponse.from(getGitHubConnection.execute());
    }

    @GetMapping("/api/github/oauth/start")
    public GitHubDtos.AuthorizeUrlResponse start() {
        return new GitHubDtos.AuthorizeUrlResponse(startGitHubOAuth.execute());
    }

    @GetMapping("/api/github/oauth/callback")
    public GitHubDtos.StatusResponse callback(
            @RequestParam String code,
            @RequestParam String state,
            Authentication authentication,
            HttpServletRequest request) {
        return GitHubDtos.StatusResponse.from(
                connectGitHub.execute(code, state, authentication.getName(), clientIp(request)));
    }

    @DeleteMapping("/api/github/connection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(Authentication authentication, HttpServletRequest request) {
        disconnectGitHub.execute(authentication.getName(), clientIp(request));
    }

    @GetMapping("/api/github/repositories")
    public List<GitHubDtos.RepositoryResponse> repositories() {
        return listGitHubRepositories.execute().stream()
                .map(GitHubDtos.RepositoryResponse::from)
                .toList();
    }

    @GetMapping("/api/github/repositories/{owner}/{repo}/branches")
    public List<GitHubDtos.BranchResponse> branches(
            @PathVariable String owner,
            @PathVariable String repo) {
        return listGitHubBranches.execute(owner, repo).stream()
                .map(GitHubDtos.BranchResponse::from)
                .toList();
    }

    @PostMapping("/api/github/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public GitHubDtos.CreateProjectResponse createProject(@RequestBody GitHubDtos.CreateProjectRequest request) {
        CreateProjectFromRepo.Result result = createProjectFromRepo.execute(
                request.owner(),
                request.repo(),
                request.branch(),
                request.projectId());
        return GitHubDtos.CreateProjectResponse.from(result);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
