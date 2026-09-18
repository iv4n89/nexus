package com.ivan.nexus.interfaces.project;

import com.ivan.nexus.application.github.GetProjectGitHubStatus;
import com.ivan.nexus.application.github.ProjectGitHubStatusView;
import com.ivan.nexus.application.log.GetRecentErrors;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.application.project.GetProject;
import com.ivan.nexus.application.project.GetProjectServices;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjectController.class)
@Import({DiscoverProjects.class, GetProject.class, GetProjectServices.class, SecurityConfig.class})
class ProjectControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ContainerInventory inventory;

    @MockitoBean
    GetRecentErrors getRecentErrors;

    @MockitoBean
    ManifestCatalog manifests;

    @MockitoBean
    GetProjectGitHubStatus getProjectGitHubStatus;

    @BeforeEach
    void setUpManifestCatalog() {
        given(manifests.discoverProjectIds()).willReturn(Set.of("lab"));
        given(manifests.exists("lab")).willReturn(true);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void listsLabProjectAsHealthyWhenTwoContainersAreRunning() throws Exception {
        given(inventory.listAll()).willReturn(List.of(
                snapshot("web-id", "lab-web-1", "running", "healthy", "web"),
                snapshot("api-id", "lab-api-1", "running", "healthy", "api")));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("lab"))
                .andExpect(jsonPath("$[0].name").value("lab"))
                .andExpect(jsonPath("$[0].status").value("HEALTHY"))
                .andExpect(jsonPath("$[0].runningCount").value(2))
                .andExpect(jsonPath("$[0].totalCount").value(2))
                .andExpect(jsonPath("$[0].deployable").value(true))
                .andExpect(jsonPath("$[0].services").doesNotExist())
                .andExpect(jsonPath("$[0].recentErrors").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownProjectReturns404ProjectNotFound() throws Exception {
        given(inventory.listAll()).willReturn(List.of(
                snapshot("web-id", "lab-web-1", "running", "healthy", "web")));

        mockMvc.perform(get("/api/projects/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void returnsGitHubStatusForLinkedProject() throws Exception {
        given(getProjectGitHubStatus.execute("lab")).willReturn(new ProjectGitHubStatusView(
                "lab", "octo", "lab-repo", "main", "abc", "abc", true));

        mockMvc.perform(get("/api/projects/lab/github-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("lab"))
                .andExpect(jsonPath("$.owner").value("octo"))
                .andExpect(jsonPath("$.repo").value("lab-repo"))
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.remoteHeadSha").value("abc"))
                .andExpect(jsonPath("$.deployedCommitSha").value("abc"))
                .andExpect(jsonPath("$.upToDate").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void manifestOnlyProjectWithNoContainersIsFound() throws Exception {
        given(inventory.listAll()).willReturn(List.of());

        mockMvc.perform(get("/api/projects/lab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("lab"))
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.runningCount").value(0))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.deployable").value(true))
                .andExpect(jsonPath("$.services", hasSize(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void projectDetailIncludesServices() throws Exception {
        given(inventory.listAll()).willReturn(List.of(
                snapshot("web-id", "lab-web-1", "running", "healthy", "web"),
                snapshot("api-id", "lab-api-1", "running", "healthy", "api")));

        mockMvc.perform(get("/api/projects/lab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("lab"))
                .andExpect(jsonPath("$.status").value("HEALTHY"))
                .andExpect(jsonPath("$.deployable").value(true))
                .andExpect(jsonPath("$.services", hasSize(2)))
                .andExpect(jsonPath("$.services[0].id").exists())
                .andExpect(jsonPath("$.services[0].name").exists())
                .andExpect(jsonPath("$.services[0].state").value("running"))
                .andExpect(jsonPath("$.services[0].status").exists())
                .andExpect(jsonPath("$.services[0].health").exists())
                .andExpect(jsonPath("$.recentErrors", hasSize(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void projectDetailIncludesRecentErrors() throws Exception {
        given(inventory.listAll()).willReturn(List.of(
                snapshot("web-id", "lab-web-1", "running", "healthy", "web")));
        given(getRecentErrors.execute("lab")).willReturn(List.of(
                new GetRecentErrors.RecentError(
                        "api",
                        "Connection to 10.0.0.31 failed at 12:42",
                        37L,
                        Instant.parse("2026-01-01T00:40:00Z"),
                        Instant.parse("2026-01-01T00:42:00Z"))));

        mockMvc.perform(get("/api/projects/lab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentErrors", hasSize(1)))
                .andExpect(jsonPath("$.recentErrors[0].serviceId").value("api"))
                .andExpect(jsonPath("$.recentErrors[0].sampleMessage")
                        .value("Connection to 10.0.0.31 failed at 12:42"))
                .andExpect(jsonPath("$.recentErrors[0].count").value(37))
                .andExpect(jsonPath("$.recentErrors[0].firstSeen").value("2026-01-01T00:40:00Z"))
                .andExpect(jsonPath("$.recentErrors[0].lastSeen").value("2026-01-01T00:42:00Z"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void projectErrorsFiltersByService() throws Exception {
        given(inventory.listAll()).willReturn(List.of(
                snapshot("api-id", "lab-api-1", "running", "healthy", "api")));
        given(getRecentErrors.execute("lab", "api")).willReturn(List.of(
                new GetRecentErrors.RecentError(
                        "api",
                        "Broken pipe",
                        12L,
                        Instant.parse("2026-01-01T00:40:00Z"),
                        Instant.parse("2026-01-01T00:42:00Z"))));

        mockMvc.perform(get("/api/projects/lab/errors").param("serviceId", "api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].serviceId").value("api"))
                .andExpect(jsonPath("$[0].sampleMessage").value("Broken pipe"))
                .andExpect(jsonPath("$[0].count").value(12));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void projectErrorsWithoutServiceReturnsProjectScopedList() throws Exception {
        given(inventory.listAll()).willReturn(List.of(
                snapshot("api-id", "lab-api-1", "running", "healthy", "api")));
        given(getRecentErrors.execute("lab", null)).willReturn(List.of(
                new GetRecentErrors.RecentError(
                        "api",
                        "Broken pipe",
                        12L,
                        Instant.parse("2026-01-01T00:40:00Z"),
                        Instant.parse("2026-01-01T00:42:00Z"))));

        mockMvc.perform(get("/api/projects/lab/errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].serviceId").value("api"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void projectServicesListsServicesForProject() throws Exception {
        given(inventory.listAll()).willReturn(List.of(
                snapshot("web-id", "lab-web-1", "running", "healthy", "web")));

        mockMvc.perform(get("/api/projects/lab/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("web"))
                .andExpect(jsonPath("$[0].name").value("web"))
                .andExpect(jsonPath("$[0].state").value("running"))
                .andExpect(jsonPath("$[0].status").value("Up 2 minutes"))
                .andExpect(jsonPath("$[0].health").value("healthy"));
    }

    @Test
    @WithAnonymousUser
    void unauthenticatedProjectsReturns401() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void projectJsonDoesNotLeakEnvSecrets() throws Exception {
        given(inventory.listAll()).willReturn(List.of(
                snapshot("web-id", "lab-web-1", "running", "healthy", "web")));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("POSTGRES_PASSWORD"))));
        mockMvc.perform(get("/api/projects/lab"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("POSTGRES_PASSWORD"))));
    }

    private static ContainerSnapshot snapshot(
            String id, String name, String state, String health, String service) {
        return new ContainerSnapshot(
                id,
                name,
                "nginx:alpine",
                "Up 2 minutes",
                state,
                health,
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("nexus.project", "lab", "nexus.service", service),
                List.of(new ContainerSnapshot.PortMapping(8080, 80)),
                0,
                Instant.parse("2026-01-01T00:00:01Z"));
    }
}
