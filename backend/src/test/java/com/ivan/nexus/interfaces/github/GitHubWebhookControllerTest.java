package com.ivan.nexus.interfaces.github;

import com.ivan.nexus.application.github.HandleGitHubPush;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import com.ivan.nexus.infrastructure.github.GitHubWebhookSignature;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import com.ivan.nexus.interfaces.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GitHubWebhookController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class GitHubWebhookControllerTest {

    private static final String SECRET = "test-webhook-secret";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    HandleGitHubPush handleGitHubPush;

    @MockitoBean
    NexusProperties properties;

    private final NexusProperties.GitHub github = new NexusProperties.GitHub();

    @BeforeEach
    void setUp() {
        github.setWebhookSecret(SECRET);
        when(properties.getGithub()).thenReturn(github);
    }

    @Test
    void acceptsSignedPushWithoutAuthentication() throws Exception {
        String body = """
                {
                  "ref": "refs/heads/main",
                  "after": "abc123def456",
                  "repository": {
                    "name": "app",
                    "owner": { "login": "acme" }
                  }
                }
                """;
        when(handleGitHubPush.execute("acme", "app", "main", "abc123def456")).thenReturn(1);

        mockMvc.perform(post("/api/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "push")
                        .header("X-Hub-Signature-256", GitHubWebhookSignature.sign(SECRET, body.getBytes(StandardCharsets.UTF_8)))
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.matchedProjects").value(1))
                .andExpect(jsonPath("$.status").value("accepted"));

        verify(handleGitHubPush).execute("acme", "app", "main", "abc123def456");
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        String body = "{\"ref\":\"refs/heads/main\"}";

        mockMvc.perform(post("/api/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "push")
                        .header("X-Hub-Signature-256", "sha256:deadbeef")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("GITHUB_WEBHOOK_INVALID"));

        verify(handleGitHubPush, never()).execute(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void ignoresNonPushEvents() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/api/github/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "ping")
                        .header("X-Hub-Signature-256", GitHubWebhookSignature.sign(SECRET, body.getBytes(StandardCharsets.UTF_8)))
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.matchedProjects").value(0))
                .andExpect(jsonPath("$.status").value("ignored"));

        verify(handleGitHubPush, never()).execute(eq("acme"), anyString(), anyString(), anyString());
    }
}
