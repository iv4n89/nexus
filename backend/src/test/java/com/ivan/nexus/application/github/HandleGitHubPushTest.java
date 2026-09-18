package com.ivan.nexus.application.github;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.deployment.DeployProject;
import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandleGitHubPushTest {

    @Mock
    DeployProject deployProject;

    @Mock
    RecordActivity recordActivity;

    private FakeStore projects;
    private HandleGitHubPush handleGitHubPush;

    @BeforeEach
    void setUp() {
        projects = new FakeStore();
        handleGitHubPush = new HandleGitHubPush(projects, deployProject, recordActivity);
    }

    @Test
    void updatesRemoteShaAndSkipsDeployWhenAutodeployDisabled() {
        projects.add(new ProjectGitHubLink("lab", "acme", "app", "main", false, null));

        int matched = handleGitHubPush.execute("acme", "app", "main", "abc123");

        assertThat(matched).isEqualTo(1);
        assertThat(projects.lastSha("lab")).isEqualTo("abc123");
        verify(deployProject, never()).execute(eq("lab"), eq(HandleGitHubPush.SYSTEM_USER), any());
        verify(recordActivity).execute(
                eq(ActivityType.GITHUB_PUSH),
                eq("lab"),
                isNull(),
                any(),
                anyMap());
    }

    @Test
    void triggersDeployWithPushShaWhenAutodeployEnabled() {
        projects.add(new ProjectGitHubLink("lab", "acme", "app", "main", true, null));
        when(deployProject.execute("lab", HandleGitHubPush.SYSTEM_USER, "def456"))
                .thenReturn(new Deployment(
                        UUID.randomUUID(),
                        "lab",
                        DeploymentStatus.PENDING,
                        Instant.now(),
                        null,
                        HandleGitHubPush.SYSTEM_USER,
                        "def456",
                        null,
                        null,
                        null,
                        "deploy"));

        int matched = handleGitHubPush.execute("acme", "app", "main", "def456");

        assertThat(matched).isEqualTo(1);
        assertThat(projects.lastSha("lab")).isEqualTo("def456");
        verify(deployProject).execute("lab", HandleGitHubPush.SYSTEM_USER, "def456");
    }

    @Test
    void ignoresUnrelatedRepositories() {
        projects.add(new ProjectGitHubLink("lab", "acme", "other", "main", true, null));

        int matched = handleGitHubPush.execute("acme", "app", "main", "abc123");

        assertThat(matched).isZero();
        verify(deployProject, never()).execute(eq("lab"), eq(HandleGitHubPush.SYSTEM_USER), any());
    }

    private static final class FakeStore implements ManagedProjectStore {
        private final List<ProjectGitHubLink> links = new ArrayList<>();

        void add(ProjectGitHubLink link) {
            links.add(link);
        }

        String lastSha(String projectId) {
            return links.stream()
                    .filter(link -> link.projectId().equals(projectId))
                    .map(ProjectGitHubLink::lastRemoteSha)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public void upsert(com.ivan.nexus.domain.manifest.ProjectManifest manifest,
                           java.nio.file.Path workingDirectory,
                           java.nio.file.Path manifestPath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void linkGitHub(String projectId, String owner, String repo, String branch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProjectGitHubLink> findGitHubLink(String projectId) {
            return links.stream().filter(link -> link.projectId().equals(projectId)).findFirst();
        }

        @Override
        public List<ProjectGitHubLink> findByGitHubRepository(String owner, String repo, String branch) {
            return links.stream()
                    .filter(link -> link.owner().equals(owner)
                            && link.repo().equals(repo)
                            && link.branch().equals(branch))
                    .toList();
        }

        @Override
        public void updateLastRemoteSha(String projectId, String sha) {
            for (int i = 0; i < links.size(); i++) {
                ProjectGitHubLink link = links.get(i);
                if (link.projectId().equals(projectId)) {
                    links.set(i, new ProjectGitHubLink(
                            link.projectId(),
                            link.owner(),
                            link.repo(),
                            link.branch(),
                            link.autodeployEnabled(),
                            sha));
                }
            }
        }

        @Override
        public void setAutodeployEnabled(String projectId, boolean enabled) {
            throw new UnsupportedOperationException();
        }
    }
}
