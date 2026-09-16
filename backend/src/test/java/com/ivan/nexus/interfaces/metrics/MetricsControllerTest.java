package com.ivan.nexus.interfaces.metrics;

import com.ivan.nexus.application.metrics.ContainerStatsProvider;
import com.ivan.nexus.application.metrics.GetProjectMetrics;
import com.ivan.nexus.application.metrics.GetSystemMetrics;
import com.ivan.nexus.application.metrics.SystemMetricsProvider;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.application.project.GetProject;
import com.ivan.nexus.domain.metrics.SystemMetrics;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MetricsController.class)
@Import({
        GetSystemMetrics.class,
        GetProjectMetrics.class,
        GetProject.class,
        DiscoverProjects.class,
        SecurityConfig.class
})
class MetricsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SystemMetricsProvider systemMetricsProvider;

    @MockitoBean
    ContainerStatsProvider containerStatsProvider;

    @MockitoBean
    ContainerInventory inventory;

    @Test
    @WithMockUser(roles = "VIEWER")
    void systemMetricsReturnsProviderValues() throws Exception {
        given(systemMetricsProvider.get()).willReturn(new SystemMetrics(
                12.5,
                1_000L,
                2_000L,
                3_000L,
                4_000L,
                0.75,
                3600L));

        mockMvc.perform(get("/api/metrics/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpuPercent").value(12.5))
                .andExpect(jsonPath("$.memoryUsedBytes").value(1000))
                .andExpect(jsonPath("$.memoryTotalBytes").value(2000))
                .andExpect(jsonPath("$.diskUsedBytes").value(3000))
                .andExpect(jsonPath("$.diskTotalBytes").value(4000))
                .andExpect(jsonPath("$.loadAverage").value(0.75))
                .andExpect(jsonPath("$.uptimeSeconds").value(3600));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownProjectMetricsReturns404ProjectNotFound() throws Exception {
        given(inventory.listAll()).willReturn(List.of());

        mockMvc.perform(get("/api/projects/missing/metrics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
    }
}
