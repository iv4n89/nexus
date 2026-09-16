package com.ivan.nexus.interfaces.database;

import com.ivan.nexus.application.database.DiscoverProjectDatabases;
import com.ivan.nexus.application.database.EditDatabaseCell;
import com.ivan.nexus.application.database.GetDatabaseMetadata;
import com.ivan.nexus.application.database.PreviewTable;
import com.ivan.nexus.application.database.RunDatabaseQuery;
import com.ivan.nexus.domain.database.QueryResult;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DatabaseController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DatabaseControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DiscoverProjectDatabases discover;
    @MockitoBean
    GetDatabaseMetadata getMetadata;
    @MockitoBean
    PreviewTable previewTable;
    @MockitoBean
    RunDatabaseQuery runQuery;
    @MockitoBean
    EditDatabaseCell editCell;

    @Test
    void unauthenticatedQueryReturns401() throws Exception {
        mockMvc.perform(post("/api/projects/lab/database/instances/lab:aaaaaaaaaaaa/query")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statement\":\"SELECT 1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerSelectReturns200() throws Exception {
        given(runQuery.execute(eq("lab"), eq("lab:aaaaaaaaaaaa"), eq("SELECT 1"), eq(false), eq("VIEWER"), eq("viewer"), any()))
                .willReturn(new QueryResult(List.of("?column?"), List.of(List.of(1)), false, 1, 1));

        mockMvc.perform(post("/api/projects/lab/database/instances/lab:aaaaaaaaaaaa/query")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statement\":\"SELECT 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(1));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerDeleteReturns403() throws Exception {
        given(runQuery.execute(eq("lab"), eq("lab:aaaaaaaaaaaa"), eq("DELETE FROM t"), anyBoolean(), eq("VIEWER"), eq("viewer"), any()))
                .willThrow(new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed"));

        mockMvc.perform(post("/api/projects/lab/database/instances/lab:aaaaaaaaaaaa/query")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statement\":\"DELETE FROM t\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("QUERY_NOT_ALLOWED"));
    }
}
