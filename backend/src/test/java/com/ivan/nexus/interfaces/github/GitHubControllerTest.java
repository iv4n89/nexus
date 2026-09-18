package com.ivan.nexus.interfaces.github;

import com.ivan.nexus.application.github.ConnectGitHub;
import com.ivan.nexus.application.github.DisconnectGitHub;
import com.ivan.nexus.application.github.GetGitHubConnection;
import com.ivan.nexus.application.github.GitHubConnectionView;
import com.ivan.nexus.application.github.GitHubRepositorySummary;
import com.ivan.nexus.application.github.ListGitHubBranches;
import com.ivan.nexus.application.github.ListGitHubRepositories;
import com.ivan.nexus.application.github.StartGitHubOAuth;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GitHubController.class)
@Import(SecurityConfig.class)
class GitHubControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetGitHubConnection getGitHubConnection;
    @MockitoBean
    StartGitHubOAuth startGitHubOAuth;
    @MockitoBean
    ConnectGitHub connectGitHub;
    @MockitoBean
    DisconnectGitHub disconnectGitHub;
    @MockitoBean
    ListGitHubRepositories listGitHubRepositories;
    @MockitoBean
    ListGitHubBranches listGitHubBranches;

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCanReadStatus() throws Exception {
        when(getGitHubConnection.execute()).thenReturn(
                new GitHubConnectionView(true, "octocat", "repo", Instant.parse("2026-09-18T08:00:00Z")));

        mockMvc.perform(get("/api/github/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.githubLogin").value("octocat"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotStartOAuth() throws Exception {
        mockMvc.perform(get("/api/github/oauth/start"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminStartsOAuth() throws Exception {
        when(startGitHubOAuth.execute()).thenReturn("https://github.com/login/oauth/authorize?state=x");

        mockMvc.perform(get("/api/github/oauth/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizeUrl").value("https://github.com/login/oauth/authorize?state=x"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCompletesCallback() throws Exception {
        when(connectGitHub.execute(eq("code"), eq("state"), eq("admin"), any()))
                .thenReturn(new GitHubConnectionView(true, "octocat", "repo", Instant.now()));

        mockMvc.perform(get("/api/github/oauth/callback").param("code", "code").param("state", "state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminDisconnects() throws Exception {
        mockMvc.perform(delete("/api/github/connection").with(csrf()))
                .andExpect(status().isNoContent());
        verify(disconnectGitHub).execute(eq("admin"), any());
    }

    @Test
    @WithAnonymousUser
    void anonymousStatusIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/github/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCanListRepositories() throws Exception {
        when(listGitHubRepositories.execute()).thenReturn(List.of(
                new GitHubRepositorySummary(1L, "octocat/lab", "lab", "octocat", "main", false)));

        mockMvc.perform(get("/api/github/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("octocat/lab"))
                .andExpect(jsonPath("$[0].defaultBranch").value("main"));
    }
}
