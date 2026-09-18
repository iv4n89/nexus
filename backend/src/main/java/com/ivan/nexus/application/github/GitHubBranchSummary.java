package com.ivan.nexus.application.github;

public record GitHubBranchSummary(String name, String commitSha, boolean protectedBranch) {
}
