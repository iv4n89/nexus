package com.ivan.nexus.application.github;

import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProjectGitHubStatusTest {
    @Mock
    ManagedProjectStore projects;
    @Mock
    DeploymentStore deployments;
    @Mock
    GitHubClient gitHubClient;
    @Mock
    RequireGitHubAccessToken accessToken;
    @InjectMocks
    GetProjectGitHubStatus useCase;

    @Test
    void comparesRemoteHeadAgainstLatestSuccessfulCommit() {
        when(projects.findGitHubLink("lab")).thenReturn(Optional.of(
                ProjectGitHubLink.of("lab", "octo", "lab-repo", "main")));
        when(accessToken.execute()).thenReturn("token");
        when(gitHubClient.getBranchHead("token", "octo", "lab-repo", "main")).thenReturn("abc123");
        when(deployments.findProjectHistoryNewestFirst("lab")).thenReturn(List.of(
                deployment(DeploymentStatus.FAILED, "old"),
                deployment(DeploymentStatus.SUCCESS, "abc123"),
                deployment(DeploymentStatus.SUCCESS, "older")));

        ProjectGitHubStatusView view = useCase.execute("lab");

        assertThat(view.remoteHeadSha()).isEqualTo("abc123");
        assertThat(view.deployedCommitSha()).isEqualTo("abc123");
        assertThat(view.upToDate()).isTrue();
        assertThat(view.owner()).isEqualTo("octo");
        assertThat(view.repo()).isEqualTo("lab-repo");
        assertThat(view.branch()).isEqualTo("main");
        assertThat(view.autodeployEnabled()).isFalse();
    }

    @Test
    void reportsBehindWhenRemoteDiffers() {
        when(projects.findGitHubLink("lab")).thenReturn(Optional.of(
                ProjectGitHubLink.of("lab", "octo", "lab-repo", "main")));
        when(accessToken.execute()).thenReturn("token");
        when(gitHubClient.getBranchHead("token", "octo", "lab-repo", "main")).thenReturn("newsha");
        when(deployments.findProjectHistoryNewestFirst("lab")).thenReturn(List.of(
                deployment(DeploymentStatus.SUCCESS, "oldsha")));

        ProjectGitHubStatusView view = useCase.execute("lab");

        assertThat(view.upToDate()).isFalse();
        assertThat(view.deployedCommitSha()).isEqualTo("oldsha");
    }

    @Test
    void prefersWebhookSyncedRemoteShaOverApi() {
        when(projects.findGitHubLink("lab")).thenReturn(Optional.of(
                new ProjectGitHubLink("lab", "octo", "lab-repo", "main", true, "webhook-sha")));
        when(deployments.findProjectHistoryNewestFirst("lab")).thenReturn(List.of(
                deployment(DeploymentStatus.SUCCESS, "oldsha")));

        ProjectGitHubStatusView view = useCase.execute("lab");

        assertThat(view.remoteHeadSha()).isEqualTo("webhook-sha");
        assertThat(view.autodeployEnabled()).isTrue();
        assertThat(view.upToDate()).isFalse();
    }

    @Test
    void throwsWhenProjectNotLinked() {
        when(projects.findGitHubLink("lab")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("lab"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.PROJECT_GITHUB_NOT_LINKED);
    }

    private static Deployment deployment(DeploymentStatus status, String commitSha) {
        return new Deployment(
                UUID.randomUUID(),
                "lab",
                status,
                null,
                null,
                "admin",
                commitSha,
                0,
                null,
                true,
                "deploy");
    }
}
