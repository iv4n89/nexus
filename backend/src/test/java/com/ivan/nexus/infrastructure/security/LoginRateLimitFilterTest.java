package com.ivan.nexus.infrastructure.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitFilterTest {

    @Test
    void returns429AfterConfiguredFailedAttempts() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-09-17T08:00:00Z"), ZoneOffset.UTC);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(clock, 3, Duration.ofMinutes(10));

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(loginFrom("203.0.113.10"), response, unauthorized());
            assertThat(response.getStatus()).isEqualTo(401);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(loginFrom("203.0.113.10"), blocked, unauthorized());
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("Too many login attempts");
    }

    @Test
    void doesNotTrustForwardedForFromThePublicInternet() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-09-17T08:00:00Z"), ZoneOffset.UTC);
        LoginRateLimitFilter filter = new LoginRateLimitFilter(clock, 1, Duration.ofMinutes(10));

        MockHttpServletRequest first = loginFrom("198.51.100.9");
        first.addHeader("X-Forwarded-For", "203.0.113.10");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, unauthorized());
        assertThat(firstResponse.getStatus()).isEqualTo(401);

        MockHttpServletRequest spoofed = loginFrom("198.51.100.9");
        spoofed.addHeader("X-Forwarded-For", "192.0.2.1");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(spoofed, blocked, unauthorized());
        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    private static MockHttpServletRequest loginFrom(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static FilterChain unauthorized() {
        return (request, response) -> ((MockHttpServletResponse) response).setStatus(401);
    }
}
