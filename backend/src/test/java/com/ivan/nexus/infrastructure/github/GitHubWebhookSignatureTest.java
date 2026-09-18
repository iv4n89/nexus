package com.ivan.nexus.infrastructure.github;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubWebhookSignatureTest {

    @Test
    void acceptsValidSignature() {
        String secret = "nexus-webhook-secret";
        byte[] body = "{\"ref\":\"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8);
        String signature = GitHubWebhookSignature.sign(secret, body);

        assertThat(GitHubWebhookSignature.isValid(secret, body, signature)).isTrue();
    }

    @Test
    void rejectsTamperedBody() {
        String secret = "nexus-webhook-secret";
        byte[] body = "{\"ref\":\"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8);
        String signature = GitHubWebhookSignature.sign(secret, body);
        byte[] tampered = "{\"ref\":\"refs/heads/evil\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(GitHubWebhookSignature.isValid(secret, tampered, signature)).isFalse();
    }

    @Test
    void rejectsWrongSecret() {
        String secret = "nexus-webhook-secret";
        byte[] body = "{\"ref\":\"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8);
        String signature = GitHubWebhookSignature.sign(secret, body);

        assertThat(GitHubWebhookSignature.isValid("other-secret", body, signature)).isFalse();
    }

    @Test
    void rejectsMissingSignature() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(GitHubWebhookSignature.isValid("secret", body, null)).isFalse();
        assertThat(GitHubWebhookSignature.isValid("secret", body, "")).isFalse();
        assertThat(GitHubWebhookSignature.isValid("", body, "sha256=abc")).isFalse();
    }

    @Test
    void signUsesSha256Prefix() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        assertThat(GitHubWebhookSignature.sign("s", body)).startsWith("sha256=");
    }
}
