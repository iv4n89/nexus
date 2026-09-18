package com.ivan.nexus.interfaces.traffic;

import com.ivan.nexus.application.traffic.GetProjectTraffic;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;
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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TrafficController.class)
@Import(SecurityConfig.class)
class TrafficControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetProjectTraffic getProjectTraffic;

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerReadsAggregatedTraffic() throws Exception {
        given(getProjectTraffic.execute("lab", 24)).willReturn(new TrafficSnapshot(
                "lab",
                Instant.parse("2026-09-17T12:00:00Z"),
                Instant.parse("2026-09-18T12:00:00Z"),
                100,
                10,
                2000,
                90,
                5,
                4,
                1,
                42.5,
                80.0));

        mockMvc.perform(get("/api/projects/{id}/traffic", "lab").param("hours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("lab"))
                .andExpect(jsonPath("$.requests").value(100))
                .andExpect(jsonPath("$.bytesOut").value(2000))
                .andExpect(jsonPath("$.status2xx").value(90))
                .andExpect(jsonPath("$.status5xx").value(1))
                .andExpect(jsonPath("$.latencyAvg").value(42.5));

        verify(getProjectTraffic).execute("lab", 24);
    }
}
