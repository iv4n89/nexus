package com.ivan.nexus.interfaces.deployment;

import com.ivan.nexus.application.deployment.DeployProject;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEntity;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import com.ivan.nexus.infrastructure.sse.DeploymentStreamHub;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeploymentController.class)
@Import(SecurityConfig.class)
class DeploymentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DeployProject deployProject;

    @MockitoBean
    DeploymentJpaRepository deployments;

    @MockitoBean
    DeploymentStreamHub hub;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPostDeployReturns202() throws Exception {
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        given(deployProject.execute("lab", "admin")).willReturn(deployment(id, DeploymentStatus.RUNNING));

        mockMvc.perform(post("/api/projects/lab/deploy").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void secondPostReturns409DeploymentInProgress() throws Exception {
        given(deployProject.execute(eq("lab"), any()))
                .willThrow(new DomainException(NexusErrorCode.DEPLOYMENT_IN_PROGRESS, "Deployment already in progress"));

        mockMvc.perform(post("/api/projects/lab/deploy").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEPLOYMENT_IN_PROGRESS"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerPostDeployReturns403() throws Exception {
        mockMvc.perform(post("/api/projects/lab/deploy").with(csrf()))
                .andExpect(status().isForbidden());
        verify(deployProject, never()).execute(any(), any());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerGetHistoryReturns200() throws Exception {
        UUID id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        given(deployments.findByProjectIdOrderByCreatedAtDesc("lab"))
                .willReturn(List.of(entity(id, "lab", DeploymentStatus.SUCCESS)));

        mockMvc.perform(get("/api/projects/lab/deployments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].projectId").value("lab"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].startedAt").exists())
                .andExpect(jsonPath("$[0].finishedAt").exists())
                .andExpect(jsonPath("$[0].triggeredBy").value("admin"))
                .andExpect(jsonPath("$[0].commitSha").isEmpty())
                .andExpect(jsonPath("$[0].exitCode").value(0))
                .andExpect(jsonPath("$[0].outputSummary").value("done"))
                .andExpect(jsonPath("$[0].healthOk").value(true));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void getStreamUnknownReturns404() throws Exception {
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        given(deployments.findById(id)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/deployments/{id}/stream", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEPLOYMENT_NOT_FOUND"));
    }

    private static Deployment deployment(UUID id, DeploymentStatus status) {
        return new Deployment(id, "lab", status, Instant.parse("2026-01-01T00:00:00Z"), null, "admin", null, null, null, null);
    }

    private static DeploymentEntity entity(UUID id, String projectId, DeploymentStatus status) {
        return new DeploymentEntity(
                id,
                projectId,
                status,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:23Z"),
                "admin",
                null,
                0,
                "done",
                true);
    }
}
