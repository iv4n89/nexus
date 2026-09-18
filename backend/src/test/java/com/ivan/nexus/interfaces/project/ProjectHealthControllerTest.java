package com.ivan.nexus.interfaces.project;

import com.ivan.nexus.application.project.GetProjectHealth;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjectHealthController.class)
@Import(SecurityConfig.class)
class ProjectHealthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetProjectHealth getProjectHealth;

    @Test
    @WithMockUser(roles = "VIEWER")
    void returnsAggregatedHealth() throws Exception {
        given(getProjectHealth.execute("lab")).willReturn(new GetProjectHealth.Result(
                "lab",
                "HEALTHY",
                2,
                2,
                DeploymentStatus.SUCCESS,
                Instant.parse("2026-01-02T00:00:00Z"),
                1L,
                3,
                true,
                Instant.parse("2026-01-01T12:00:00Z"),
                2));

        mockMvc.perform(get("/api/projects/lab/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("lab"))
                .andExpect(jsonPath("$.projectStatus").value("HEALTHY"))
                .andExpect(jsonPath("$.containersRunning").value(2))
                .andExpect(jsonPath("$.containersTotal").value(2))
                .andExpect(jsonPath("$.lastDeploymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.lastDeploymentAt").value("2026-01-02T00:00:00Z"))
                .andExpect(jsonPath("$.openAlertsCount").value(1))
                .andExpect(jsonPath("$.openSecurityFindingsCount").value(3))
                .andExpect(jsonPath("$.backupsAvailable").value(true))
                .andExpect(jsonPath("$.lastBackupSuccessAt").value("2026-01-01T12:00:00Z"))
                .andExpect(jsonPath("$.domainsCount").value(2));
    }
}
