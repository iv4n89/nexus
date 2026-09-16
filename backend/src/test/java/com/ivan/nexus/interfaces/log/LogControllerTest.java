package com.ivan.nexus.interfaces.log;

import com.ivan.nexus.application.log.GetContainerLogs;
import com.ivan.nexus.application.log.LogProvider;
import com.ivan.nexus.application.log.SearchLogs;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.domain.container.ContainerSnapshot;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LogController.class)
@Import({GetContainerLogs.class, SearchLogs.class, SecurityConfig.class})
class LogControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ContainerInventory inventory;

    @MockitoBean
    LogProvider logProvider;

    @Test
    @WithMockUser(roles = "VIEWER")
    void getLogsReturnsFetchedWindow() throws Exception {
        given(inventory.findById("abc123")).willReturn(Optional.of(snapshot()));
        given(logProvider.fetch(eq("abc123"), eq(200), isNull(), isNull(), eq(true)))
                .willReturn(List.of("line1", "line2"));

        mockMvc.perform(get("/api/containers/abc123/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[0]").value("line1"))
                .andExpect(jsonPath("$.lines[1]").value("line2"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownContainerLogsReturns404ContainerNotFound() throws Exception {
        given(inventory.findById("missing")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/containers/missing/logs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONTAINER_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void clampsTailBelowOneToDefaultAndAboveMaxTo2000() throws Exception {
        given(inventory.findById("abc123")).willReturn(Optional.of(snapshot()));
        given(logProvider.fetch(eq("abc123"), anyInt(), isNull(), isNull(), eq(true)))
                .willReturn(List.of());

        mockMvc.perform(get("/api/containers/abc123/logs").param("tail", "0"))
                .andExpect(status().isOk());
        verify(logProvider).fetch(eq("abc123"), eq(200), isNull(), isNull(), eq(true));

        mockMvc.perform(get("/api/containers/abc123/logs").param("tail", "5000"))
                .andExpect(status().isOk());
        verify(logProvider).fetch(eq("abc123"), eq(2000), isNull(), isNull(), eq(true));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void passesSinceUntilAndTimestamps() throws Exception {
        given(inventory.findById("abc123")).willReturn(Optional.of(snapshot()));
        given(logProvider.fetch(eq("abc123"), eq(50), eq(100), eq(200), eq(false)))
                .willReturn(List.of("ts line"));

        mockMvc.perform(get("/api/containers/abc123/logs")
                        .param("tail", "50")
                        .param("since", "100")
                        .param("until", "200")
                        .param("timestamps", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0]").value("ts line"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void searchFiltersByQueryAndLevel() throws Exception {
        given(inventory.findById("abc123")).willReturn(Optional.of(snapshot()));
        given(logProvider.fetch(eq("abc123"), eq(200), isNull(), isNull(), eq(true)))
                .willReturn(List.of(
                        "ERROR timeout on db",
                        "INFO timeout ignored",
                        "ERROR unrelated"));

        mockMvc.perform(get("/api/containers/abc123/logs/search")
                        .param("q", "timeout")
                        .param("level", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andExpect(jsonPath("$.lines[0]").value("ERROR timeout on db"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchWithoutQueryOrLevelReturnsWindow() throws Exception {
        given(inventory.findById("abc123")).willReturn(Optional.of(snapshot()));
        given(logProvider.fetch(eq("abc123"), eq(200), isNull(), isNull(), eq(true)))
                .willReturn(List.of("INFO started", "ERROR boom"));

        mockMvc.perform(get("/api/containers/abc123/logs/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[0]").value("INFO started"))
                .andExpect(jsonPath("$.lines[1]").value("ERROR boom"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownContainerSearchReturns404ContainerNotFound() throws Exception {
        given(inventory.findById("missing")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/containers/missing/logs/search").param("q", "timeout"))
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
                Map.of("nexus.project", "lab"),
                List.of(),
                1,
                Instant.parse("2026-01-01T00:00:01Z"));
    }
}
