package com.ivan.nexus.application.github;

/**
 * Short-lived OAuth {@code state} values for CSRF protection on the callback.
 */
public interface GitHubOAuthStateStore {
    String issue();

    boolean consume(String state);
}
