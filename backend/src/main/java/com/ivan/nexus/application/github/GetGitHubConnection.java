package com.ivan.nexus.application.github;

import org.springframework.stereotype.Service;

@Service
public class GetGitHubConnection {
    private final GitHubConnectionStore connectionStore;

    public GetGitHubConnection(GitHubConnectionStore connectionStore) {
        this.connectionStore = connectionStore;
    }

    public GitHubConnectionView execute() {
        return connectionStore.load()
                .map(GitHubConnectionView::from)
                .orElseGet(GitHubConnectionView::disconnected);
    }
}
