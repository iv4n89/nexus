package com.ivan.nexus.interfaces.database;

import com.ivan.nexus.application.database.DiscoverProjectDatabases;
import com.ivan.nexus.application.database.EditDatabaseCells;
import com.ivan.nexus.application.database.EditDatabaseCellsCommand;
import com.ivan.nexus.application.database.GetDatabaseMetadata;
import com.ivan.nexus.application.database.PreviewTable;
import com.ivan.nexus.application.database.RunDatabaseQuery;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import com.ivan.nexus.interfaces.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    EditDatabaseCells editCells;

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

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void previewQueryFailedIncludesSqlMessage() throws Exception {
        given(previewTable.execute(eq("lab"), eq("lab:aaaaaaaaaaaa"), eq("public"), eq("jobs"), any(), any(), eq("ADMIN")))
                .willThrow(new DomainException(NexusErrorCode.QUERY_FAILED, "relation \"jobs\" does not exist"));

        mockMvc.perform(get("/api/projects/lab/database/instances/lab:aaaaaaaaaaaa/preview")
                        .queryParam("schema", "public")
                        .queryParam("table", "jobs"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("QUERY_FAILED"))
                .andExpect(jsonPath("$.error.message").value("relation \"jobs\" does not exist"));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerPreviewOfControlPlaneDatabaseReturns403() throws Exception {
        given(previewTable.execute(eq("nexus"), eq("nexus:cccccccccccc"), any(), any(), any(), any(), eq("VIEWER")))
                .willThrow(new DomainException(NexusErrorCode.FORBIDDEN, "Control-plane database is admin-only"));

        mockMvc.perform(get("/api/projects/nexus/database/instances/nexus:cccccccccccc/preview")
                        .queryParam("schema", "public")
                        .queryParam("table", "users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerPostCellsReturns403() throws Exception {
        mockMvc.perform(post("/api/projects/lab/database/instances/lab:aaaaaaaaaaaa/cells")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schema\":\"public\",\"table\":\"jobs\",\"patches\":[{\"primaryKey\":{\"id\":1},\"column\":\"status\",\"value\":\"running\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPostCellIsGone() throws Exception {
        mockMvc.perform(post("/api/projects/lab/database/instances/lab:aaaaaaaaaaaa/cell")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schema\":\"public\",\"table\":\"jobs\",\"primaryKey\":{\"id\":1},\"column\":\"status\",\"value\":\"running\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPostCellsReturns200() throws Exception {
        given(editCells.execute(any(EditDatabaseCellsCommand.class)))
                .willReturn(new QueryResult(List.of("updateCount"), List.of(List.of(1)), false, 1, 1));
        mockMvc.perform(post("/api/projects/lab/database/instances/lab:aaaaaaaaaaaa/cells")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": "public",
                                  "table": "jobs",
                                  "mongoDatabase": "app",
                                  "collection": "events",
                                  "patches": [
                                    {"primaryKey": {"id": 1}, "column": "status", "value": null,
                                     "id": "doc-1", "field": "state"}
                                  ],
                                  "inserts": [{"values": {"name": null, "rank": 2}}],
                                  "deletes": [{"id": 7, "tenant": null}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(1));

        ArgumentCaptor<EditDatabaseCellsCommand> command =
                ArgumentCaptor.forClass(EditDatabaseCellsCommand.class);
        org.mockito.Mockito.verify(editCells).execute(command.capture());
        EditDatabaseCellsCommand input = command.getValue();
        assertEquals("lab", input.projectId());
        assertEquals("lab:aaaaaaaaaaaa", input.databaseId());
        assertEquals("ADMIN", input.role());
        assertEquals("admin", input.username());
        assertEquals("203.0.113.9", input.ip());
        assertEquals("public", input.schema());
        assertEquals("jobs", input.table());
        assertEquals("app", input.mongoDatabase());
        assertEquals("events", input.collection());
        assertEquals(Map.of("id", 1), input.patches().getFirst().primaryKey());
        assertEquals("status", input.patches().getFirst().column());
        assertNull(input.patches().getFirst().value());
        assertEquals("doc-1", input.patches().getFirst().id());
        assertEquals("state", input.patches().getFirst().field());
        assertEquals(2, input.inserts().getFirst().values().get("rank"));
        assertNull(input.inserts().getFirst().values().get("name"));
        assertEquals(7, input.deletes().getFirst().get("id"));
        assertNull(input.deletes().getFirst().get("tenant"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void nullCellListsMapToEmptyApplicationLists() throws Exception {
        given(editCells.execute(any(EditDatabaseCellsCommand.class)))
                .willReturn(new QueryResult(List.of(), List.of(), false, 1, 0));

        mockMvc.perform(post("/api/projects/lab/database/instances/lab:aaaaaaaaaaaa/cells")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": "public",
                                  "table": "jobs",
                                  "patches": null,
                                  "inserts": null,
                                  "deletes": null
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<EditDatabaseCellsCommand> command =
                ArgumentCaptor.forClass(EditDatabaseCellsCommand.class);
        org.mockito.Mockito.verify(editCells).execute(command.capture());
        assertEquals(List.of(), command.getValue().patches());
        assertEquals(List.of(), command.getValue().inserts());
        assertEquals(List.of(), command.getValue().deletes());
    }
}
