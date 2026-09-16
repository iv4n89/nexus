package com.ivan.nexus.interfaces.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "NEXUS_ADMIN_USERNAME=admin",
        "NEXUS_ADMIN_PASSWORD=changeme"
})
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void csrfReturnsToken() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void loginWithValidCredentialsSetsSessionCookie() throws Exception {
        Csrf csrf = csrf();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"changeme\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(request().sessionAttribute("SPRING_SECURITY_CONTEXT", notNullValue()))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotBlank();
    }

    @Test
    void loginRotatesSessionIdToPreventFixation() throws Exception {
        Csrf csrf = csrf();
        MockHttpSession preLoginSession = new MockHttpSession();
        String sessionIdBefore = preLoginSession.getId();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(preLoginSession)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"changeme\"}"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession sessionAfter = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(sessionAfter).isNotNull();
        assertThat(sessionAfter.getId()).isNotEqualTo(sessionIdBefore);
        assertThat(sessionAfter.getId()).isNotBlank();
    }

    @Test
    void successfulLoginWritesAuditEventWithoutPassword() throws Exception {
        Csrf csrf = csrf();
        int before = countLoginEvents();

        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"changeme\"}"))
                .andExpect(status().isOk());

        assertThat(countLoginEvents()).isEqualTo(before + 1);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                        SELECT user_id, action, ip, metadata
                        FROM audit_events
                        WHERE action = 'LOGIN'
                        ORDER BY created_at DESC
                        LIMIT 1
                        """);
        assertThat(row.get("action")).isEqualTo("LOGIN");
        assertThat(row.get("user_id")).isNotNull();
        assertThat(row.get("ip")).isEqualTo("203.0.113.10");

        JsonNode metadata = objectMapper.readTree(row.get("metadata").toString());
        assertThat(metadata.has("password")).isFalse();
    }

    @Test
    void loginWithBadPasswordReturns401() throws Exception {
        Csrf csrf = csrf();

        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID"));
    }

    @Test
    void meWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meAfterLoginReturnsCurrentUser() throws Exception {
        MockHttpSession session = login("admin", "changeme");

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void unauthenticatedProjectsReturns401() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    private int countLoginEvents() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'LOGIN'", Integer.class);
        return count == null ? 0 : count;
    }

    private MockHttpSession login(String username, String password) throws Exception {
        Csrf csrf = csrf();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private Csrf csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        String token = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
        return new Csrf(cookie, token);
    }

    private record Csrf(Cookie cookie, String token) {}
}
