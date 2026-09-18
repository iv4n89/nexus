package com.ivan.nexus.application.github;

import com.ivan.nexus.application.deployment.DeployProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HandleGitHubPush {
    public static final String SYSTEM_USER = "github-webhook";

    private static final Logger log = LoggerFactory.getLogger(HandleGitHubPush.class);

    private final ProjectGitHubLinkStore links;
    private final DeployProject deployProject;

    public HandleGitHubPush(ProjectGitHubLinkStore links, DeployProject deployProject) {
        this.links = links;
        this.deployProject = deployProject;
    }

    @Transactional
    public int execute(String owner, String repo, String branch, String afterSha) {
        List<ProjectGitHubLink> matched = links.findByRepository(owner, repo, branch);
        for (ProjectGitHubLink link : matched) {
            links.updateLastRemoteSha(link.projectId(), afterSha);
            if (link.autoDeploy()) {
                try {
                    deployProject.execute(link.projectId(), SYSTEM_USER);
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
