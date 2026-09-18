package com.ivan.nexus.infrastructure.github;

import com.ivan.nexus.application.github.GitHubBranchSummary;
import com.ivan.nexus.application.github.GitHubClient;
import com.ivan.nexus.application.github.GitHubIdentity;
import com.ivan.nexus.application.github.GitHubOAuthToken;
import com.ivan.nexus.application.github.GitHubRepositorySummary;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

public class HttpGitHubClient implements GitHubClient {
    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String DEFAULT_SCOPES = "repo read:user";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public HttpGitHubClient(RestClient.Builder restClientBuilder, NexusProperties properties) {
        NexusProperties.GitHub github = properties.getGithub();
        this.clientId = blankToNull(github.getClientId());
        this.clientSecret = blankToNull(github.getClientSecret());
        this.redirectUri = blankToNull(github.getRedirectUri());
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", DEFAULT_SCOPES)
                .queryParam("state", state)
                .build(true)
                .toUriString();
    }

    @Override
    public GitHubOAuthToken exchangeCode(String code) {
        requireConfigured();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "client_id", clientId,
                            "client_secret", clientSecret,
                            "code", code,
                            "redirect_uri", redirectUri))
                    .retrieve()
                    .body(Map.class);
            if (body == null || body.get("access_token") == null) {
                throw new DomainException(NexusErrorCode.GITHUB_OAUTH_FAILED, "GitHub did not return an access token");
            }
            if (body.get("error") != null) {
                throw new DomainException(
                        NexusErrorCode.GITHUB_OAUTH_FAILED,
                        String.valueOf(body.getOrDefault("error_description", body.get("error"))));
            }
            return new GitHubOAuthToken(
                    String.valueOf(body.get("access_token")),
                    body.get("refresh_token") == null ? null : String.valueOf(body.get("refresh_token")),
                    body.get("scope") == null ? null : String.valueOf(body.get("scope")),
                    body.get("token_type") == null ? null : String.valueOf(body.get("token_type")));
        } catch (DomainException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new DomainException(NexusErrorCode.GITHUB_OAUTH_FAILED, "GitHub token exchange failed");
        }
    }

    @Override
    public GitHubIdentity getAuthenticatedUser(String accessToken) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri(USER_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .body(Map.class);
            if (body == null || body.get("login") == null) {
                throw new DomainException(NexusErrorCode.GITHUB_OAUTH_FAILED, "GitHub user lookup failed");
            }
            long id = body.get("id") instanceof Number number ? number.longValue() : 0L;
            return new GitHubIdentity(String.valueOf(body.get("login")), id);
        } catch (DomainException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new DomainException(NexusErrorCode.GITHUB_OAUTH_FAILED, "GitHub user lookup failed");
        }
    }

    @Override
    public List<GitHubRepositorySummary> listRepositories(String accessToken) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> body = restClient.get()
                    .uri("https://api.github.com/user/repos?per_page=100&sort=updated")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .body(List.class);
            if (body == null) {
                return List.of();
            }
            return body.stream().map(HttpGitHubClient::toRepository).toList();
        } catch (RestClientResponseException ex) {
            throw new DomainException(NexusErrorCode.GITHUB_OAUTH_FAILED, "GitHub repository listing failed");
        }
    }

    @Override
    public List<GitHubBranchSummary> listBranches(String accessToken, String owner, String repo) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> body = restClient.get()
                    .uri("https://api.github.com/repos/{owner}/{repo}/branches?per_page=100", owner, repo)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .body(List.class);
            if (body == null) {
                return List.of();
            }
            return body.stream().map(HttpGitHubClient::toBranch).toList();
        } catch (RestClientResponseException ex) {
            throw new DomainException(NexusErrorCode.GITHUB_OAUTH_FAILED, "GitHub branch listing failed");
        }
    }

    @SuppressWarnings("unchecked")
    private static GitHubRepositorySummary toRepository(Map<String, Object> row) {
        Map<String, Object> owner = row.get("owner") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        long id = row.get("id") instanceof Number number ? number.longValue() : 0L;
        boolean privateRepo = Boolean.TRUE.equals(row.get("private"));
        return new GitHubRepositorySummary(
                id,
                String.valueOf(row.getOrDefault("full_name", "")),
                String.valueOf(row.getOrDefault("name", "")),
                String.valueOf(owner.getOrDefault("login", "")),
                String.valueOf(row.getOrDefault("default_branch", "main")),
                privateRepo);
    }

    @SuppressWarnings("unchecked")
    private static GitHubBranchSummary toBranch(Map<String, Object> row) {
        Map<String, Object> commit = row.get("commit") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        return new GitHubBranchSummary(
                String.valueOf(row.getOrDefault("name", "")),
                String.valueOf(commit.getOrDefault("sha", "")),
                Boolean.TRUE.equals(row.get("protected")));
    }

    private void requireConfigured() {
        if (clientId == null || clientSecret == null || redirectUri == null) {
            throw new IllegalStateException("GitHub OAuth is not configured (client-id, client-secret, redirect-uri)");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
