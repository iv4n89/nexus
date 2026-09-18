package com.ivan.nexus.application.github;

import java.util.List;

public interface ProjectGitHubLinkStore {
    List<ProjectGitHubLink> findByRepository(String owner, String repo, String branch);

    void updateLastRemoteSha(String projectId, String sha);
}
