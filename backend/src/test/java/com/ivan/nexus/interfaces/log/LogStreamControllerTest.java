package com.ivan.nexus.interfaces.log;

import com.ivan.nexus.application.log.LogProvider;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LogStreamController.class)
@Import({SecurityConfig.class, LogStreamControllerTest.SyncSseExecutor.class})
class LogStreamControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ContainerInventory inventory;

    @MockitoBean
    LogProvider logProvider;

    @TestConfiguration
    static class SyncSseExecutor {
        @Bean(name = "sseExecutor")
        Executor sseExecutor() {
            return Runnable::run;
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownContainerStreamReturns404ContainerNotFound() throws Exception {
        given(inventory.findById("missing")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/containers/missing/logs/stream"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONTAINER_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void streamSendsNamedLogEventsAndDisablesProxyBuffering() throws Exception {
        given(inventory.findById("abc123")).willReturn(Optional.of(snapshot()));
        AtomicReference<Consumer<String>> onLine = new AtomicReference<>();
        AtomicReference<Runnable> onComplete = new AtomicReference<>();
        given(logProvider.follow(eq("abc123"), eq(100), isNull(), any(), any()))
                .willAnswer(invocation -> {
                    onLine.set(invocation.getArgument(3));
                    onComplete.set(invocation.getArgument(4));
                    return (AutoCloseable) () -> {
                    };
                });

        MvcResult result = mockMvc.perform(get("/api/containers/abc123/logs/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn();

        onLine.get().accept("INFO started");
        onComplete.get().run();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:log")))
                .andExpect(content().string(containsString("data:INFO started")));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void lateLogLineAfterClientDisconnectDoesNotFailTheFollow() throws Exception {
        given(inventory.findById("abc123")).willReturn(Optional.of(snapshot()));
        AtomicReference<Consumer<String>> onLine = new AtomicReference<>();
        AtomicReference<Runnable> onComplete = new AtomicReference<>();
        given(logProvider.follow(eq("abc123"), eq(100), isNull(), any(), any()))
                .willAnswer(invocation -> {
                    onLine.set(invocation.getArgument(3));
                    onComplete.set(invocation.getArgument(4));
                    return (AutoCloseable) () -> {
                    };
                });

        mockMvc.perform(get("/api/containers/abc123/logs/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());

        onComplete.get().run();
        onLine.get().accept("ERROR after disconnect");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void streamClampsTailAndPassesSince() throws Exception {
        given(inventory.findById("abc123")).willReturn(Optional.of(snapshot()));
        given(logProvider.follow(eq("abc123"), anyInt(), any(), any(), any()))
                .willReturn(() -> {
                });

        mockMvc.perform(get("/api/containers/abc123/logs/stream")
                        .param("tail", "0")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
        verify(logProvider).follow(eq("abc123"), eq(100), isNull(), any(), any());

        mockMvc.perform(get("/api/containers/abc123/logs/stream")
                        .param("tail", "5000")
                        .param("since", "1700000000")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
        verify(logProvider).follow(eq("abc123"), eq(2000), eq(1700000000), any(), any());
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
