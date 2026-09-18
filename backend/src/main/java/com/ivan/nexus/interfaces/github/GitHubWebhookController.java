package com.ivan.nexus.interfaces.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.application.github.HandleGitHubPush;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import com.ivan.nexus.infrastructure.github.GitHubWebhookSignature;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class GitHubWebhookController {
    private final NexusProperties properties;
    private final HandleGitHubPush handleGitHubPush;
    private final ObjectMapper objectMapper;

    public GitHubWebhookController(
            NexusProperties properties,
            HandleGitHubPush handleGitHubPush,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.handleGitHubPush = handleGitHubPush;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/github/webhook")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WebhookAck handle(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestBody byte[] body) {
        String secret = properties.getGithub().getWebhookSecret();
        if (!GitHubWebhookSignature.isValid(secret, body, signature)) {
            throw new DomainException(NexusErrorCode.GITHUB_WEBHOOK_INVALID, "Invalid webhook signature");
        }
        if (event == null || !"push".equalsIgnoreCase(event)) {
            return new WebhookAck(0, "ignored");
        }
        PushPayload push = parsePush(body);
        if (push == null) {
            return new WebhookAck(0, "ignored");
        }
        int matched = handleGitHubPush.execute(push.owner(), push.repo(), push.branch(), push.afterSha());
        return new WebhookAck(matched, "accepted");
    }

    private PushPayload parsePush(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String after = text(root, "after");
            if (after == null || after.isBlank() || "0000000000000000000000000000000000000000".equals(after)) {
                return null;
            }
            String ref = text(root, "ref");
            if (ref == null || !ref.startsWith("refs/heads/")) {
                return null;
            }
            String branch = ref.substring("refs/heads/".length());
            JsonNode repository = root.path("repository");
            String owner = text(repository.path("owner"), "login");
            if (owner == null || owner.isBlank()) {
                owner = text(repository.path("owner"), "name");
            }
            String repo = text(repository, "name");
            if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
                return null;
            }
            return new PushPayload(owner, repo, branch, after);
        } catch (IOException e) {
            throw new DomainException(NexusErrorCode.GITHUB_WEBHOOK_INVALID, "Invalid webhook payload");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public record WebhookAck(int matchedProjects, String status) {
    }

    private record PushPayload(String owner, String repo, String branch, String afterSha) {
    }
}
