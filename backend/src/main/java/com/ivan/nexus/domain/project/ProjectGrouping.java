package com.ivan.nexus.domain.project;

import java.util.Locale;
import java.util.Map;

public final class ProjectGrouping {

    private static final String NEXUS_PROJECT = "nexus.project";
    private static final String COMPOSE_PROJECT = "com.docker.compose.project";
    private static final String COMPOSE_WORKING_DIR = "com.docker.compose.project.working_dir";
    private static final String COMPOSE_CONFIG_FILES = "com.docker.compose.project.config_files";
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

    /** Compose project directory on the host (where {@code .env} usually lives). */
    public static String composeWorkingDir(Map<String, String> labels) {
        if (labels == null) {
            return null;
        }
        String value = labels.get(COMPOSE_WORKING_DIR);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        String files = labels.get(COMPOSE_CONFIG_FILES);
        if (files == null || files.isBlank()) {
            return null;
        }
        String first = files.split(",")[0].trim();
        int slash = Math.max(first.lastIndexOf('/'), first.lastIndexOf('\\'));
        if (slash <= 0) {
            return null;
        }
        return first.substring(0, slash);
    }

    /** Lowercase and treat {@code -} / {@code _} as equivalent. */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static boolean matches(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        String a = normalize(left);
        String b = normalize(right);
        return !a.isEmpty() && a.equals(b);
    }

    public static boolean belongsTo(
            String containerName,
            Map<String, String> labels,
            String projectId,
            String directoryName) {
        String grouped = projectId(containerName, labels);
        if (matches(grouped, projectId)) {
            return true;
        }
        return directoryName != null && !directoryName.isBlank() && matches(grouped, directoryName);
    }

    private static String stripLeadingSlash(String name) {
        if (name != null && name.startsWith("/")) {
            return name.substring(1);
        }
        return name;
    }
}
