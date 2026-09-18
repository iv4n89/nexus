package com.ivan.nexus.application.github;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.deployment.DeployProject;
import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.domain.activity.ActivityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class HandleGitHubPush {
    public static final String SYSTEM_USER = "github-webhook";

    private static final Logger log = LoggerFactory.getLogger(HandleGitHubPush.class);

    private final ManagedProjectStore projects;
    private final DeployProject deployProject;
    private final RecordActivity recordActivity;

    public HandleGitHubPush(
            ManagedProjectStore projects,
            DeployProject deployProject,
            RecordActivity recordActivity) {
        this.projects = projects;
        this.deployProject = deployProject;
        this.recordActivity = recordActivity;
    }

    @Transactional
    public int execute(String owner, String repo, String branch, String afterSha) {
        List<ProjectGitHubLink> matched = projects.findByGitHubRepository(owner, repo, branch);
        for (ProjectGitHubLink link : matched) {
            projects.updateLastRemoteSha(link.projectId(), afterSha);
            recordActivity.execute(
                    ActivityType.GITHUB_PUSH,
                    link.projectId(),
                    null,
                    "GitHub push on " + owner + "/" + repo + "@" + branch,
                    Map.of(
                            "owner", owner,
                            "repo", repo,
                            "branch", branch,
                            "commitSha", afterSha,
                            "autodeployEnabled", link.autodeployEnabled()));
            if (link.autodeployEnabled()) {
                try {
                    deployProject.execute(link.projectId(), SYSTEM_USER, afterSha);
                } catch (RuntimeException ex) {
                    log.warn(
                            "Auto-deploy failed for project {} after webhook push: {}",
                            link.projectId(),
                            ex.getMessage());
                }
            }
        }
        return matched.size();
    }
}
