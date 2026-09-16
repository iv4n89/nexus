package com.ivan.nexus.interfaces.container;

import com.ivan.nexus.application.metrics.ContainerStatsProvider;
import com.ivan.nexus.application.metrics.GetContainerMetrics;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.metrics.ContainerMetrics;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ContainerController.class)
@Import({GetContainerMetrics.class, SecurityConfig.class})
class ContainerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ContainerInventory inventory;

    @MockitoBean
    ContainerStatsProvider statsProvider;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listsContainersWithoutEnv() throws Exception {
        given(inventory.listAll()).willReturn(List.of(snapshot()));

        mockMvc.perform(get("/api/containers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("abc123"))
                .andExpect(jsonPath("$[0].name").value("lab-web-1"))
                .andExpect(jsonPath("$[0].image").value("nginx:alpine"))
                .andExpect(jsonPath("$[0].status").value("Up 2 minutes"))
                .andExpect(jsonPath("$[0].state").value("running"))
                .andExpect(jsonPath("$[0].health").value("healthy"))
                .andExpect(jsonPath("$[0].created").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$[0].labels['nexus.project']").value("lab"))
                .andExpect(jsonPath("$[0].ports[0].publicPort").value(8080))
                .andExpect(jsonPath("$[0].ports[0].privatePort").value(80))
                .andExpect(jsonPath("$[0].restartCount").value(1))
                .andExpect(jsonPath("$[0].startedAt").value("2026-01-01T00:00:01Z"))
                .andExpect(jsonPath("$[0].env").doesNotExist())
                .andExpect(content().string(not(containsString("POSTGRES_PASSWORD"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownContainerReturns404ContainerNotFound() throws Exception {
        given(inventory.findById("missing")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/containers/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONTAINER_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void getContainerReturnsSnapshotWithoutEnv() throws Exception {
        given(inventory.findById("abc123")).willReturn(Optional.of(snapshot()));

        mockMvc.perform(get("/api/containers/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc123"))
                .andExpect(jsonPath("$.env").doesNotExist())
                .andExpect(content().string(not(containsString("POSTGRES_PASSWORD"))));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void containerStatsReturnsProviderValues() throws Exception {
        given(inventory.findById("abc123")).willReturn(Optional.of(snapshot()));
        given(statsProvider.stats("abc123"))
                .willReturn(new ContainerMetrics("abc123", 12.5, 100, 200, 10, 20));

        mockMvc.perform(get("/api/containers/abc123/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containerId").value("abc123"))
                .andExpect(jsonPath("$.cpuPercent").value(12.5))
                .andExpect(jsonPath("$.memoryUsedBytes").value(100))
                .andExpect(jsonPath("$.memoryLimitBytes").value(200))
                .andExpect(jsonPath("$.rxBytes").value(10))
                .andExpect(jsonPath("$.txBytes").value(20));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownContainerStatsReturns404ContainerNotFound() throws Exception {
        given(inventory.findById("missing")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/containers/missing/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONTAINER_NOT_FOUND"));
    }

    private static ContainerSnapshot snapshot() {
        return new ContainerSnapshot(
                "abc123",
                "lab-web-1",
                "nginx:alpine",
                "Up 2 minutes",
                "running",
                "healthy",
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("nexus.project", "lab", "nexus.service", "web"),
                List.of(new ContainerSnapshot.PortMapping(8080, 80)),
                1,
                Instant.parse("2026-01-01T00:00:01Z"));
    }
}
