package com.ivan.nexus.application.github;

import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@Service
public class CreateProjectFromRepo {
    private final RequireGitHubAccessToken accessToken;
    private final GitHubClient gitHubClient;
    private final GitCheckout gitCheckout;
    private final ManifestCatalog manifests;
    private final ManagedProjectStore projects;
    private final Path allowedRoot;

    public CreateProjectFromRepo(
            RequireGitHubAccessToken accessToken,
            GitHubClient gitHubClient,
            GitCheckout gitCheckout,
            ManifestCatalog manifests,
            ManagedProjectStore projects,
            @Qualifier("manifestAllowedRoot") Path allowedRoot) {
        this.accessToken = accessToken;
        this.gitHubClient = gitHubClient;
        this.gitCheckout = gitCheckout;
        this.manifests = manifests;
        this.projects = projects;
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
    }

    @Transactional
    public Result execute(String owner, String repo, String branch, String projectId) {
        String normalizedOwner = requireNonBlank(owner, "owner");
        String normalizedRepo = requireNonBlank(repo, "repo");
        String normalizedBranch = requireNonBlank(branch, "branch");
        String resolvedProjectId = resolveProjectId(projectId, normalizedRepo);

        String token = accessToken.execute();
        gitHubClient.getBranchHead(token, normalizedOwner, normalizedRepo, normalizedBranch);

        Path targetDir = allowedRoot.resolve(resolvedProjectId).toAbsolutePath().normalize();
        String cloneUrl = authenticatedCloneUrl(token, normalizedOwner, normalizedRepo);
        GitCheckout.CheckoutResult checkout = gitCheckout.cloneOrUpdate(cloneUrl, normalizedBranch, targetDir);
        if (!checkout.hasNexusYml()) {
            throw new DomainException(
                    NexusErrorCode.MANIFEST_NOT_FOUND,
                    "Checked-out repository does not contain nexus.yml");
        }
        if (!checkout.hasDeploySh()) {
            throw new DomainException(
                    NexusErrorCode.GITHUB_CHECKOUT_FAILED,
                    "Checked-out repository does not contain deploy.sh");
        }

        LoadedManifest loaded = manifests.loadRequired(resolvedProjectId);
        projects.upsert(
                loaded.manifest(),
                Path.of(loaded.manifest().project().workingDirectory()).toAbsolutePath().normalize(),
                loaded.manifestPath());
        projects.linkGitHub(resolvedProjectId, normalizedOwner, normalizedRepo, normalizedBranch);

        return new Result(
                resolvedProjectId,
                loaded.manifest().project().name(),
                normalizedOwner,
                normalizedRepo,
                normalizedBranch,
                checkout.hasNexusYml(),
                checkout.hasDeploySh());
    }

    private static String resolveProjectId(String projectId, String repo) {
        String candidate = projectId == null || projectId.isBlank() ? repo : projectId.trim();
        if (!isSafePathSegment(candidate)) {
            throw new DomainException(
                    NexusErrorCode.OPERATION_NOT_ALLOWED,
                    "projectId must be a single safe path segment");
        }
        return candidate;
    }

    private static String authenticatedCloneUrl(String token, String owner, String repo) {
        return "https://x-access-token:" + token + "@github.com/" + owner + "/" + repo + ".git";
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(NexusErrorCode.GITHUB_OAUTH_FAILED, field + " is required");
        }
        return value.trim();
    }

    private static boolean isSafePathSegment(String projectId) {
        if (projectId == null
                || projectId.isBlank()
                || ".".equals(projectId)
                || "..".equals(projectId)
                || projectId.contains("/")
                || projectId.contains("\\")) {
            return false;
        }
        try {
            Path path = Path.of(projectId);
            return !path.isAbsolute() && path.getNameCount() == 1;
        } catch (InvalidPathException ignored) {
            return false;
        }
    }

    public record Result(
            String projectId,
            String name,
            String owner,
            String repo,
            String branch,
            boolean hasNexusYml,
            boolean hasDeploySh) {
    }
}
