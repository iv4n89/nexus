package com.ivan.nexus.application.deployment;

import java.util.List;

final class DeploymentSummary {
    static final int SUMMARY_LIMIT = 8000;

    private DeploymentSummary() {
    }

    static String summarize(List<String> lines) {
        String joined = String.join("\n", lines);
        if (joined.length() <= SUMMARY_LIMIT) {
            return joined;
        }
        return joined.substring(joined.length() - SUMMARY_LIMIT);
    }
}
