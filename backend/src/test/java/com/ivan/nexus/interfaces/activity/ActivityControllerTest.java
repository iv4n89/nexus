package com.ivan.nexus.interfaces.activity;

import com.ivan.nexus.application.activity.GetActivityTimeline;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import com.ivan.nexus.infrastructure.sse.ActivityHub;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActivityController.class)
@Import(SecurityConfig.class)
class ActivityControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetActivityTimeline getActivityTimeline;

    @MockitoBean
    ActivityHub hub;

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerListsRecentActivity() throws Exception {
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        given(getActivityTimeline.execute(50)).willReturn(List.of(new ActivityResponse(
                id,
                Instant.parse("2026-01-01T00:42:12Z"),
                ActivityType.DEPLOYMENT_STARTED,
                "lab",
                "api",
                "deployment started",
                Map.of())));

        mockMvc.perform(get("/api/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].createdAt").value("2026-01-01T00:42:12Z"))
                .andExpect(jsonPath("$[0].type").value("DEPLOYMENT_STARTED"))
                .andExpect(jsonPath("$[0].projectId").value("lab"))
                .andExpect(jsonPath("$[0].serviceId").value("api"))
                .andExpect(jsonPath("$[0].message").value("deployment started"));

        verify(getActivityTimeline).execute(50);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCanRequestCustomLimit() throws Exception {
        given(getActivityTimeline.execute(8)).willReturn(List.of());

        mockMvc.perform(get("/api/activity").param("limit", "8"))
                .andExpect(status().isOk());

        verify(getActivityTimeline).execute(8);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCanSubscribeToGlobalStream() throws Exception {
        mockMvc.perform(get("/api/events/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andExpect(header().string("X-Accel-Buffering", "no"));

        verify(hub).subscribe(any(SseEmitter.class));
    }
}
