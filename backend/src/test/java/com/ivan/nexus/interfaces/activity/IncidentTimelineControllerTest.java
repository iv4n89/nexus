package com.ivan.nexus.interfaces.activity;

import com.ivan.nexus.application.activity.GetIncidentTimeline;
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
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IncidentTimelineController.class)
@Import(SecurityConfig.class)
class IncidentTimelineControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetIncidentTimeline getIncidentTimeline;

    @Test
    @WithMockUser(roles = "VIEWER")
    void returnsMergedTimeline() throws Exception {
        UUID ref = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        given(getIncidentTimeline.execute("lab", 50)).willReturn(List.of(
                new GetIncidentTimeline.Item(
                        Instant.parse("2026-01-01T01:00:00Z"),
                        "DEPLOYMENT_SUCCESS",
                        "ACTIVITY",
                        "deploy done",
                        "api",
                        ref)));

        mockMvc.perform(get("/api/projects/lab/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].at").value("2026-01-01T01:00:00Z"))
                .andExpect(jsonPath("$[0].kind").value("DEPLOYMENT_SUCCESS"))
                .andExpect(jsonPath("$[0].source").value("ACTIVITY"))
                .andExpect(jsonPath("$[0].message").value("deploy done"))
                .andExpect(jsonPath("$[0].serviceId").value("api"))
                .andExpect(jsonPath("$[0].refId").value(ref.toString()));

        verify(getIncidentTimeline).execute("lab", 50);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void acceptsCustomLimit() throws Exception {
        given(getIncidentTimeline.execute("lab", 8)).willReturn(List.of());

        mockMvc.perform(get("/api/projects/lab/timeline").param("limit", "8"))
                .andExpect(status().isOk());

        verify(getIncidentTimeline).execute("lab", 8);
    }
}
