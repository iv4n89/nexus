package com.ivan.nexus.interfaces.alert;

import com.ivan.nexus.application.alert.AcknowledgeAlert;
import com.ivan.nexus.application.alert.GetAlerts;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
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

@WebMvcTest(controllers = AlertController.class)
@Import(SecurityConfig.class)
class AlertControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetAlerts getAlerts;

    @MockitoBean
    AcknowledgeAlert acknowledgeAlert;

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerListsOpenAlertsByDefault() throws Exception {
        UUID id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        given(getAlerts.execute(null)).willReturn(List.of(alert(id, AlertStatus.ACTIVE)));

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].type").value("CONTAINER_STOPPED"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].projectId").value("lab"))
                .andExpect(jsonPath("$[0].serviceId").value("web"))
                .andExpect(jsonPath("$[0].message").value("Container web is exited"))
                .andExpect(jsonPath("$[0].openedAt").value("2026-01-01T00:00:00Z"));

        verify(getAlerts).execute(null);
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCanFilterByStatus() throws Exception {
        given(getAlerts.execute(List.of(AlertStatus.RESOLVED))).willReturn(List.of());

        mockMvc.perform(get("/api/alerts").param("status", "RESOLVED"))
                .andExpect(status().isOk());

        verify(getAlerts).execute(List.of(AlertStatus.RESOLVED));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAcknowledgeSetsAcknowledged() throws Exception {
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        given(acknowledgeAlert.execute(eq(id), eq("admin"), any()))
                .willReturn(alert(id, AlertStatus.ACKNOWLEDGED));

        mockMvc.perform(post("/api/alerts/{id}/acknowledge", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerAcknowledgeIsForbidden() throws Exception {
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        mockMvc.perform(post("/api/alerts/{id}/acknowledge", id).with(csrf()))
                .andExpect(status().isForbidden());

        verify(acknowledgeAlert, never()).execute(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void acknowledgeMissingAlertReturns404() throws Exception {
        UUID id = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        given(acknowledgeAlert.execute(eq(id), eq("admin"), any()))
                .willThrow(new DomainException(NexusErrorCode.ALERT_NOT_FOUND, "Alert not found"));

        mockMvc.perform(post("/api/alerts/{id}/acknowledge", id).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ALERT_NOT_FOUND"));
    }

    private static Alert alert(UUID id, AlertStatus status) {
        return new Alert(
                id,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "lab",
                "web",
                status,
                "Container web is exited",
                Instant.parse("2026-01-01T00:00:00Z"),
                status == AlertStatus.ACKNOWLEDGED ? Instant.parse("2026-01-01T00:01:00Z") : null,
                null,
                AlertType.CONTAINER_STOPPED);
    }
}
