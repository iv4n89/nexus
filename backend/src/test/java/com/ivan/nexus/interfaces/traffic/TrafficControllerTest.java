package com.ivan.nexus.interfaces.traffic;

import com.ivan.nexus.application.traffic.CorrelateDeployTraffic;
import com.ivan.nexus.application.traffic.GetGlobalTraffic;
import com.ivan.nexus.application.traffic.GetProjectTraffic;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.domain.traffic.DeployTrafficDelta;
import com.ivan.nexus.domain.traffic.TrafficEndpointStat;
import com.ivan.nexus.domain.traffic.TrafficOverview;
import com.ivan.nexus.domain.traffic.TrafficProjectRanking;
import com.ivan.nexus.domain.traffic.TrafficReport;
import com.ivan.nexus.domain.traffic.TrafficSeriesPoint;
import com.ivan.nexus.domain.traffic.TrafficServiceBreakdown;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import com.ivan.nexus.interfaces.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TrafficController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TrafficControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetProjectTraffic getProjectTraffic;

    @MockitoBean
    GetGlobalTraffic getGlobalTraffic;

    @MockitoBean
    CorrelateDeployTraffic correlateDeployTraffic;

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerReadsAggregatedTraffic() throws Exception {
        Instant from = Instant.parse("2026-09-17T12:00:00Z");
        Instant to = Instant.parse("2026-09-18T12:00:00Z");
        given(getProjectTraffic.execute("lab", 24)).willReturn(new TrafficReport(
                "lab",
                from,
                to,
                new TrafficSnapshot(
                        "lab",
                        from,
                        to,
                        100,
                        10,
                        2000,
                        90,
                        5,
                        4,
                        1,
                        42.5,
                        80.0,
                        List.of(new TrafficEndpointStat("/api", 50, 30.0))),
                List.of(new TrafficSeriesPoint(from, 100, 2000, 1, 42.5, 80.0)),
                List.of(new TrafficServiceBreakdown("web", 100, 1, 42.5, 80.0))));

        mockMvc.perform(get("/api/projects/{id}/traffic", "lab").param("hours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("lab"))
                .andExpect(jsonPath("$.requests").value(100))
                .andExpect(jsonPath("$.bytesOut").value(2000))
                .andExpect(jsonPath("$.status2xx").value(90))
                .andExpect(jsonPath("$.status5xx").value(1))
                .andExpect(jsonPath("$.latencyAvgMs").value(42.5))
                .andExpect(jsonPath("$.latencyP95Ms").value(80.0))
                .andExpect(jsonPath("$.latencyMaxMs").value(80.0))
                .andExpect(jsonPath("$.topEndpoints[0].path").value("/api"))
                .andExpect(jsonPath("$.series").isArray())
                .andExpect(jsonPath("$.series[0].t").exists())
                .andExpect(jsonPath("$.series[0].requests").value(100))
                .andExpect(jsonPath("$.series[0].status5xx").value(1))
                .andExpect(jsonPath("$.series[0].latencyAvgMs").value(42.5))
                .andExpect(jsonPath("$.series[0].latencyMaxMs").value(80.0))
                .andExpect(jsonPath("$.services[0].serviceId").value("web"));

        verify(getProjectTraffic).execute("lab", 24);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerReadsGlobalTraffic() throws Exception {
        Instant from = Instant.parse("2026-09-17T12:00:00Z");
        Instant to = Instant.parse("2026-09-18T12:00:00Z");
        given(getGlobalTraffic.execute(24)).willReturn(new TrafficOverview(
                from,
                to,
                new TrafficSnapshot(
                        TrafficOverview.GLOBAL_PROJECT_ID,
                        from,
                        to,
                        150,
                        0,
                        3000,
                        140,
                        0,
                        6,
                        4,
                        30.0,
                        90.0,
                        List.of()),
                List.of(new TrafficSeriesPoint(from, 150, 3000, 4, 30.0, 90.0)),
                List.of(new TrafficProjectRanking("lab", 150, 4, 30.0))));

        mockMvc.perform(get("/api/traffic").param("hours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").exists())
                .andExpect(jsonPath("$.to").exists())
                .andExpect(jsonPath("$.hours").value(24))
                .andExpect(jsonPath("$.requests").value(150))
                .andExpect(jsonPath("$.bytesOut").value(3000))
                .andExpect(jsonPath("$.status2xx").value(140))
                .andExpect(jsonPath("$.status4xx").value(6))
                .andExpect(jsonPath("$.status5xx").value(4))
                .andExpect(jsonPath("$.latencyAvgMs").value(30.0))
                .andExpect(jsonPath("$.latencyMaxMs").value(90.0))
                .andExpect(jsonPath("$.series[0].requests").value(150))
                .andExpect(jsonPath("$.series[0].status5xx").value(4))
                .andExpect(jsonPath("$.series[0].latencyAvgMs").value(30.0))
                .andExpect(jsonPath("$.series[0].latencyMaxMs").value(90.0))
                .andExpect(jsonPath("$.projects[0].projectId").value("lab"));

        verify(getGlobalTraffic).execute(24);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void rejectsHoursBelowOne() throws Exception {
        given(getGlobalTraffic.execute(0))
                .willThrow(new DomainException(
                        NexusErrorCode.OPERATION_NOT_ALLOWED, "hours must be between 1 and 168"));

        mockMvc.perform(get("/api/traffic").param("hours", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OPERATION_NOT_ALLOWED"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void rejectsHoursAbove168() throws Exception {
        given(getProjectTraffic.execute("lab", 169))
                .willThrow(new DomainException(
                        NexusErrorCode.OPERATION_NOT_ALLOWED, "hours must be between 1 and 168"));

        mockMvc.perform(get("/api/projects/{id}/traffic", "lab").param("hours", "169"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("OPERATION_NOT_ALLOWED"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerReadsDeployDelta() throws Exception {
        UUID deploymentId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        Instant deployedAt = Instant.parse("2026-09-18T12:00:00Z");
        given(correlateDeployTraffic.execute("lab", deploymentId)).willReturn(new DeployTrafficDelta(
                "lab",
                deploymentId,
                deployedAt,
                deployedAt.minusSeconds(3600),
                deployedAt,
                deployedAt,
                deployedAt.plusSeconds(3600),
                100,
                150,
                1,
                5,
                40.0,
                90.0,
                2.333,
                50.0,
                50.0));

        mockMvc.perform(get("/api/projects/{id}/traffic/deploy-delta", "lab")
                        .param("deploymentId", deploymentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deploymentId").value(deploymentId.toString()))
                .andExpect(jsonPath("$.latencyP95DeltaMs").value(50.0))
                .andExpect(jsonPath("$.afterStatus5xx").value(5));

        verify(correlateDeployTraffic).execute("lab", deploymentId);
    }
}
