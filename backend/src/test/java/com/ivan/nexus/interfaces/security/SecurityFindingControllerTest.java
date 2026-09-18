package com.ivan.nexus.interfaces.security;

import com.ivan.nexus.application.security.AcknowledgeSecurityFinding;
import com.ivan.nexus.application.security.ListSecurityFindings;
import com.ivan.nexus.application.security.RunSecurityScan;
import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import com.ivan.nexus.domain.security.SecuritySeverity;
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

@WebMvcTest(controllers = SecurityFindingController.class)
@Import(SecurityConfig.class)
class SecurityFindingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ListSecurityFindings listSecurityFindings;

    @MockitoBean
    RunSecurityScan runSecurityScan;

    @MockitoBean
    AcknowledgeSecurityFinding acknowledgeSecurityFinding;

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerListsFindings() throws Exception {
        UUID id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        given(listSecurityFindings.execute("lab")).willReturn(List.of(finding(id, SecurityFindingStatus.OPEN)));

        mockMvc.perform(get("/api/projects/{id}/security/findings", "lab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].severity").value("HIGH"))
                .andExpect(jsonPath("$[0].packageName").value("openssl"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));

        verify(listSecurityFindings).execute("lab");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminRunsScan() throws Exception {
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        given(runSecurityScan.execute("lab")).willReturn(List.of(finding(id, SecurityFindingStatus.OPEN)));

        mockMvc.perform(post("/api/projects/{id}/security/scan", "lab").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fingerprint").value("fp-1"));

        verify(runSecurityScan).execute("lab");
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerScanIsForbidden() throws Exception {
        mockMvc.perform(post("/api/projects/{id}/security/scan", "lab").with(csrf()))
                .andExpect(status().isForbidden());

        verify(runSecurityScan, never()).execute(any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAcknowledgeSetsAcknowledged() throws Exception {
        UUID id = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        given(acknowledgeSecurityFinding.execute(eq("lab"), eq(id), eq("admin"), any()))
                .willReturn(finding(id, SecurityFindingStatus.ACKNOWLEDGED));

        mockMvc.perform(post("/api/projects/{id}/security/findings/{findingId}/acknowledge", "lab", id)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerAcknowledgeIsForbidden() throws Exception {
        UUID id = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        mockMvc.perform(post("/api/projects/{id}/security/findings/{findingId}/acknowledge", "lab", id)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(acknowledgeSecurityFinding, never()).execute(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void acknowledgeMissingFindingReturns404() throws Exception {
        UUID id = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        given(acknowledgeSecurityFinding.execute(eq("lab"), eq(id), eq("admin"), any()))
                .willThrow(new DomainException(NexusErrorCode.SECURITY_FINDING_NOT_FOUND, "Security finding not found"));

        mockMvc.perform(post("/api/projects/{id}/security/findings/{findingId}/acknowledge", "lab", id)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SECURITY_FINDING_NOT_FOUND"));
    }

    private static SecurityFinding finding(UUID id, SecurityFindingStatus status) {
        return new SecurityFinding(
                id,
                "lab",
                SecuritySeverity.HIGH,
                "trivy",
                "openssl",
                "1.0.0",
                "1.0.1",
                "CVE-2024-1",
                "fp-1",
                status,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
