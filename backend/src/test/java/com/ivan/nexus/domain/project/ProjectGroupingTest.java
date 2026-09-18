package com.ivan.nexus.domain.project;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectGroupingTest {
    @Test
    void prefersNexusProjectLabel() {
        var labels = Map.of(
            "nexus.project", "portfolio",
            "com.docker.compose.project", "portfolio-compose");
        assertEquals("portfolio", ProjectGrouping.projectId("random_name", labels));
    }

    @Test
    void fallsBackToComposeLabel() {
        var labels = Map.of("com.docker.compose.project", "lab");
        assertEquals("lab", ProjectGrouping.projectId("lab-api-1", labels));
    }

    @Test
    void fallsBackToContainerName() {
        assertEquals("nginx", ProjectGrouping.projectId("/nginx", Map.of()));
    }

    @Test
    void servicePrefersNexusThenComposeThenName() {
        assertEquals("api", ProjectGrouping.serviceId("/x", Map.of("nexus.service", "api", "com.docker.compose.service", "other")));
        assertEquals("web", ProjectGrouping.serviceId("/x", Map.of("com.docker.compose.service", "web")));
        assertEquals("nginx", ProjectGrouping.serviceId("/nginx", Map.of()));
    }

    @Test
    void matchesNormalizedHyphenUnderscoreAndCase() {
        assertTrue(ProjectGrouping.matches("foo-bar", "foo_bar"));
        assertTrue(ProjectGrouping.matches("Foo-Bar", "foo_bar"));
        assertTrue(ProjectGrouping.belongsTo(
                "x",
                Map.of("com.docker.compose.project", "foo_bar"),
                "foo-bar",
                null));
        assertTrue(ProjectGrouping.belongsTo(
                "x",
                Map.of("com.docker.compose.project", "lab_dir"),
                "other-id",
                "lab-dir"));
    }
}
