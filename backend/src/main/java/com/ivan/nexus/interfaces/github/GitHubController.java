package com.ivan.nexus.interfaces.github;

import com.ivan.nexus.application.github.ConnectGitHub;
import com.ivan.nexus.application.github.DisconnectGitHub;
import com.ivan.nexus.application.github.GetGitHubConnection;
import com.ivan.nexus.application.github.StartGitHubOAuth;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GitHubController {
    private final GetGitHubConnection getGitHubConnection;
    private final StartGitHubOAuth startGitHubOAuth;
    private final ConnectGitHub connectGitHub;
    private final DisconnectGitHub disconnectGitHub;

    public GitHubController(
            GetGitHubConnection getGitHubConnection,
            StartGitHubOAuth startGitHubOAuth,
            ConnectGitHub connectGitHub,
            DisconnectGitHub disconnectGitHub) {
        this.getGitHubConnection = getGitHubConnection;
        this.startGitHubOAuth = startGitHubOAuth;
        this.connectGitHub = connectGitHub;
        this.disconnectGitHub = disconnectGitHub;
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

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
