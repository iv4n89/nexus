package com.ivan.nexus.interfaces.env;

import com.ivan.nexus.application.env.DeleteProjectEnv;
import com.ivan.nexus.application.env.ListProjectEnv;
import com.ivan.nexus.application.env.ProjectEnvVarView;
import com.ivan.nexus.application.env.RotateProjectEnv;
import com.ivan.nexus.application.env.UpsertProjectEnv;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import com.ivan.nexus.interfaces.error.GlobalExceptionHandler;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProjectEnvController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProjectEnvControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ListProjectEnv listProjectEnv;

    @MockitoBean
    UpsertProjectEnv upsertProjectEnv;

    @MockitoBean
    RotateProjectEnv rotateProjectEnv;

    @MockitoBean
    DeleteProjectEnv deleteProjectEnv;

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerListsMaskedEnv() throws Exception {
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        given(listProjectEnv.execute("lab")).willReturn(List.of(
                new ProjectEnvVarView(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        "lab",
                        "API_KEY",
                        true,
                        null,
                        now,
                        now),
                new ProjectEnvVarView(
                        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                        "lab",
                        "NODE_ENV",
                        false,
                        "production",
                        now,
                        now)));

        mockMvc.perform(get("/api/projects/lab/env"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("API_KEY"))
                .andExpect(jsonPath("$[0].secret").value(true))
                .andExpect(jsonPath("$[0].value").value(nullValue()))
                .andExpect(jsonPath("$[1].name").value("NODE_ENV"))
                .andExpect(jsonPath("$[1].value").value("production"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminUpsertsEnv() throws Exception {
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        given(upsertProjectEnv.execute(
                eq("lab"), eq("API_KEY"), eq("s3cret"), eq(true), eq("user"), anyString()))
                .willReturn(new ProjectEnvVarView(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        "lab",
                        "API_KEY",
                        true,
                        null,
                        now,
                        now));

        mockMvc.perform(put("/api/projects/lab/env")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API_KEY",
                                  "value": "s3cret",
                                  "secret": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("API_KEY"))
                .andExpect(jsonPath("$.secret").value(true))
                .andExpect(jsonPath("$.value").value(nullValue()));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerUpsertForbidden() throws Exception {
        mockMvc.perform(put("/api/projects/lab/env")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API_KEY",
                                  "value": "s3cret",
                                  "secret": true
                                }
                                """))
                .andExpect(status().isForbidden());
        verify(upsertProjectEnv, never()).execute(
                anyString(), anyString(), anyString(), anyBoolean(), anyString(), anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminRotatesEnv() throws Exception {
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        given(rotateProjectEnv.execute(eq("lab"), eq("API_KEY"), eq("rotated"), eq("user"), anyString()))
                .willReturn(new ProjectEnvVarView(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        "lab",
                        "API_KEY",
                        true,
                        null,
                        now,
                        now));

        mockMvc.perform(post("/api/projects/lab/env/API_KEY/rotate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "rotated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("API_KEY"))
                .andExpect(jsonPath("$.value").value(nullValue()));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerRotateForbidden() throws Exception {
        mockMvc.perform(post("/api/projects/lab/env/API_KEY/rotate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "rotated"
                                }
                                """))
                .andExpect(status().isForbidden());
        verify(rotateProjectEnv, never()).execute(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rotateMissingReturnsNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException(NexusErrorCode.ENV_VAR_NOT_FOUND, "missing"))
                .when(rotateProjectEnv)
                .execute(eq("lab"), eq("MISSING"), eq("x"), eq("user"), anyString());

        mockMvc.perform(post("/api/projects/lab/env/MISSING/rotate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "x"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ENV_VAR_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDeletesEnv() throws Exception {
        mockMvc.perform(delete("/api/projects/lab/env/API_KEY").with(csrf()))
                .andExpect(status().isOk());
        verify(deleteProjectEnv).execute(eq("lab"), eq("API_KEY"), eq("user"), anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteMissingReturnsNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new DomainException(NexusErrorCode.ENV_VAR_NOT_FOUND, "missing"))
                .when(deleteProjectEnv)
                .execute(eq("lab"), eq("MISSING"), eq("user"), anyString());

        mockMvc.perform(delete("/api/projects/lab/env/MISSING").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ENV_VAR_NOT_FOUND"));
    }
}
