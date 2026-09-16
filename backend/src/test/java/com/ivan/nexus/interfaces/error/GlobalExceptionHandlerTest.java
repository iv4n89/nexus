package com.ivan.nexus.interfaces.error;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        var converter = new MappingJackson2HttpMessageConverter(mapper);

        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorFixtureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void domainExceptionProjectNotFoundReturns404Envelope() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Project not found"))
                .andExpect(jsonPath("$.error.timestamp").exists());
    }

    @Test
    void accessDeniedReturns403Forbidden() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Forbidden"));
    }

    @Test
    void databaseNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/test/database-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DATABASE_NOT_FOUND"));
    }

    @Test
    void queryTimeoutReturns504() throws Exception {
        mockMvc.perform(get("/test/query-timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("QUERY_TIMEOUT"));
    }

    @Test
    void unexpectedExceptionReturns500InternalErrorWithoutLeakage() throws Exception {
        String body = mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Internal error"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .doesNotContain("IllegalStateException")
                .doesNotContain("java.lang")
                .doesNotContain(".java:")
                .doesNotContain("at com.ivan");
    }

    @Test
    void brokenPipeDoesNotReturnInternalErrorEnvelope() throws Exception {
        mockMvc.perform(get("/test/broken-pipe"))
                .andExpect(status().isNoContent());
    }

    @Test
    void completedSseEmitterDoesNotReturnInternalErrorEnvelope() throws Exception {
        mockMvc.perform(get("/test/sse-already-completed"))
                .andExpect(status().isNoContent());
    }

    @RestController
    static class ErrorFixtureController {
        @GetMapping("/test/not-found")
        void notFound() {
            throw new DomainException(NexusErrorCode.PROJECT_NOT_FOUND, "Project not found");
        }

        @GetMapping("/test/forbidden")
        void forbidden() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret failure details");
        }

        @GetMapping("/test/database-not-found")
        void databaseNotFound() {
            throw new DomainException(NexusErrorCode.DATABASE_NOT_FOUND, "Database not found");
        }

        @GetMapping("/test/query-timeout")
        void queryTimeout() {
            throw new DomainException(NexusErrorCode.QUERY_TIMEOUT, "Query timed out");
        }

        @GetMapping("/test/broken-pipe")
        void brokenPipe() throws Exception {
            throw new java.io.IOException("Broken pipe");
        }

        @GetMapping("/test/sse-already-completed")
        void sseAlreadyCompleted() {
            throw new IllegalStateException("ResponseBodyEmitter has already completed");
        }
    }
}
