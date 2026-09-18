package com.ivan.nexus.interfaces.site;

import com.ivan.nexus.application.site.AddDomain;
import com.ivan.nexus.application.site.ListProjectDomains;
import com.ivan.nexus.application.site.RemoveDomain;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DomainController.class)
@Import(SecurityConfig.class)
class DomainControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ListProjectDomains listProjectDomains;

    @MockitoBean
    AddDomain addDomain;

    @MockitoBean
    RemoveDomain removeDomain;

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerGetListReturns200() throws Exception {
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        given(listProjectDomains.execute("lab")).willReturn(List.of(
                new SiteDomain(id, "lab", "app.example.com", "api", 8080, now, now, CertStatus.PENDING)));

        mockMvc.perform(get("/api/projects/lab/domains"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].hostname").value("app.example.com"));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerPostAddReturns403() throws Exception {
        mockMvc.perform(post("/api/projects/lab/domains")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hostname":"app.example.com","serviceName":"api","targetPort":8080}
                                """))
                .andExpect(status().isForbidden());
        verify(addDomain, never()).execute(any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPostAddReturns201() throws Exception {
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        given(addDomain.execute(eq("lab"), eq("app.example.com"), eq("api"), eq(8080), eq("admin"), any()))
                .willReturn(new SiteDomain(id, "lab", "app.example.com", "api", 8080, now, now, CertStatus.PENDING));

        mockMvc.perform(post("/api/projects/lab/domains")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hostname":"app.example.com","serviceName":"api","targetPort":8080}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.hostname").value("app.example.com"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminDeleteReturns204() throws Exception {
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        mockMvc.perform(delete("/api/projects/lab/domains/" + id).with(csrf()))
                .andExpect(status().isNoContent());

        verify(removeDomain).execute(eq("lab"), eq(id), eq("admin"), any());
    }
}
