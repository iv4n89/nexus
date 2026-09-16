package com.ivan.nexus.domain.project;

import java.util.Map;

public final class ProjectGrouping {

    private static final String NEXUS_PROJECT = "nexus.project";
    private static final String COMPOSE_PROJECT = "com.docker.compose.project";
    private static final String NEXUS_SERVICE = "nexus.service";
    private static final String COMPOSE_SERVICE = "com.docker.compose.service";

    private ProjectGrouping() {
    }

    public static String projectId(String containerName, Map<String, String> labels) {
        var nexusProject = labels.get(NEXUS_PROJECT);
        if (nexusProject != null && !nexusProject.isBlank()) {
            return nexusProject;
        }
        var composeProject = labels.get(COMPOSE_PROJECT);
        if (composeProject != null && !composeProject.isBlank()) {
            return composeProject;
        }
        return stripLeadingSlash(containerName);
    }

    public static String serviceId(String containerName, Map<String, String> labels) {
        var nexusService = labels.get(NEXUS_SERVICE);
        if (nexusService != null && !nexusService.isBlank()) {
            return nexusService;
        }
        var composeService = labels.get(COMPOSE_SERVICE);
        if (composeService != null && !composeService.isBlank()) {
            return composeService;
        }
        return stripLeadingSlash(containerName);
    }

    private static String stripLeadingSlash(String name) {
        if (name != null && name.startsWith("/")) {
            return name.substring(1);
        }
        return name;
    }
}
