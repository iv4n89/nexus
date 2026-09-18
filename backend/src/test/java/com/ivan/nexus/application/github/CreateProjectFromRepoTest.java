package com.ivan.nexus.application.github;

import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateProjectFromRepoTest {
    @TempDir
    Path allowedRoot;

    @Mock
    RequireGitHubAccessToken accessToken;
    @Mock
    GitHubClient gitHubClient;
    @Mock
    GitCheckout gitCheckout;
    @Mock
    ManifestCatalog manifests;
    @Mock
    ManagedProjectStore projects;

    private CreateProjectFromRepo useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateProjectFromRepo(
                accessToken, gitHubClient, gitCheckout, manifests, projects, allowedRoot);
    }

    @Test
    void checksOutValidatesAndLinksGitHub() {
        Path target = allowedRoot.resolve("lab");
        ProjectManifest manifest = new ProjectManifest(
                new ProjectManifest.ProjectBlock("lab", "Lab", "desc", target.toString()),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);
        when(accessToken.execute()).thenReturn("token");
        when(gitHubClient.getBranchHead("token", "octo", "lab-repo", "main")).thenReturn("sha");
        when(gitCheckout.cloneOrUpdate(any(), eq("main"), eq(target)))
                .thenReturn(new GitCheckout.CheckoutResult(target, true, true));
        when(manifests.loadRequired("lab"))
                .thenReturn(new LoadedManifest(manifest, target.resolve("nexus.yml")));

        CreateProjectFromRepo.Result result = useCase.execute("octo", "lab-repo", "main", "lab");

        assertThat(result.projectId()).isEqualTo("lab");
        assertThat(result.hasNexusYml()).isTrue();
        assertThat(result.hasDeploySh()).isTrue();
        verify(projects).upsert(eq(manifest), any(), eq(target.resolve("nexus.yml")));
        verify(projects).linkGitHub("lab", "octo", "lab-repo", "main");
        verify(gitCheckout).cloneOrUpdate(
                eq("https://x-access-token:token@github.com/octo/lab-repo.git"),
                eq("main"),
                eq(target));
    }

    @Test
    void defaultsProjectIdToRepoName() {
        Path target = allowedRoot.resolve("lab-repo");
        ProjectManifest manifest = new ProjectManifest(
                new ProjectManifest.ProjectBlock("lab-repo", "Lab", null, target.toString()),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);
        when(accessToken.execute()).thenReturn("token");
        when(gitHubClient.getBranchHead("token", "octo", "lab-repo", "main")).thenReturn("sha");
        when(gitCheckout.cloneOrUpdate(any(), eq("main"), eq(target)))
                .thenReturn(new GitCheckout.CheckoutResult(target, true, true));
        when(manifests.loadRequired("lab-repo"))
                .thenReturn(new LoadedManifest(manifest, target.resolve("nexus.yml")));

        CreateProjectFromRepo.Result result = useCase.execute("octo", "lab-repo", "main", null);

        assertThat(result.projectId()).isEqualTo("lab-repo");
    }

    @Test
    void rejectsMissingManifest() {
        Path target = allowedRoot.resolve("lab");
        when(accessToken.execute()).thenReturn("token");
        when(gitHubClient.getBranchHead("token", "octo", "lab-repo", "main")).thenReturn("sha");
        when(gitCheckout.cloneOrUpdate(any(), eq("main"), eq(target)))
                .thenReturn(new GitCheckout.CheckoutResult(target, false, true));

        assertThatThrownBy(() -> useCase.execute("octo", "lab-repo", "main", "lab"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.MANIFEST_NOT_FOUND);
    }

    @Test
    void rejectsMissingDeployScript() {
        Path target = allowedRoot.resolve("lab");
        when(accessToken.execute()).thenReturn("token");
        when(gitHubClient.getBranchHead("token", "octo", "lab-repo", "main")).thenReturn("sha");
        when(gitCheckout.cloneOrUpdate(any(), eq("main"), eq(target)))
                .thenReturn(new GitCheckout.CheckoutResult(target, true, false));

        assertThatThrownBy(() -> useCase.execute("octo", "lab-repo", "main", "lab"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.GITHUB_CHECKOUT_FAILED);
    }
}
